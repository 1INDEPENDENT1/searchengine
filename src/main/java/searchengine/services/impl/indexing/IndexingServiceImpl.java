package searchengine.services.impl;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.models.SiteEntity;
import searchengine.models.SiteStatusType;
import searchengine.repos.SiteRepository;
import searchengine.services.IndexingService;
import searchengine.services.SiteIndexingHelper;
import searchengine.services.WebScraperService;
import searchengine.services.tasks.ScrapTask;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Log4j2
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sites;
    private final SiteRepository siteRepo;
    private final WebScraperService webScraperService;
    private final SiteIndexingHelper siteIndexingHelper;
    private final List<ForkJoinPool> forkJoinPools = new ArrayList<>();


    @Autowired
    public IndexingServiceImpl(SitesList sites, SiteRepository siteRepo, WebScraperService webScraperService, SiteIndexingHelper siteIndexingHelper) {
        this.sites = sites;
        this.siteRepo = siteRepo;
        this.webScraperService = webScraperService;
        this.siteIndexingHelper = siteIndexingHelper;
    }

    @Override
    public boolean startIndexing() {
        if (!siteIndexingHelper.isIndexingInProgress()) {
            log.info("Starting indexing process.");
            List<Site> sitesList = sites.getSites();
            List<SiteEntity> entities = updateOrCreateSiteEntities(sitesList);

            for (SiteEntity siteEntity : entities) {
                int parallelism = Math.max(2, Runtime.getRuntime().availableProcessors() / entities.size());
                ForkJoinPool pool = new ForkJoinPool(parallelism);
                forkJoinPools.add(pool);
                ScrapTask task = siteIndexingHelper.prepareIndexingTask(siteEntity, pool);
                pool.submit(task);
            }

            log.info("Indexing started asynchronously for all sites.");
            return true;
        }

        log.info("Indexing has already started. New tasks have not started.");
        return false;
    }

    private List<SiteEntity> updateOrCreateSiteEntities(final List<Site> sitesList) {
        siteRepo.findAll().forEach(siteIndexingHelper::clearExistingData);

        List<SiteEntity> newEntities = new ArrayList<>();
        sitesList.forEach(site -> {
            SiteEntity newEntity = createNewSiteEntity(site);
            newEntities.add(newEntity);
        });

        return newEntities;
    }

    private SiteEntity createNewSiteEntity(final Site site) {
        final SiteEntity entity = new SiteEntity(site.getUrl(), site.getName(), SiteStatusType.INDEXING);
        siteRepo.save(entity);
        return entity;
    }


    @Override
    public boolean stopIndexing() {
        if (siteIndexingHelper.isIndexingInProgress()) {
            for (ForkJoinPool pool : forkJoinPools) {
                pool.shutdownNow();
            }
            forkJoinPools.clear();

            List<SiteEntity> indexingSites = siteRepo.findAll().stream()
                    .filter(site -> site.getStatus() == SiteStatusType.INDEXING)
                    .toList();

            for (SiteEntity site : indexingSites) {
                try {
                    Thread.sleep(3000L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                site.setStatus(SiteStatusType.FAILED);
                site.setStatusTime(LocalDateTime.now());
                site.setLastError("Индексация была остановлена вручную");
                siteRepo.save(site);
            }

            return true;
        }
        return false;
    }

    public Map<String, Object> handlePageUpdate(String urlStr) {
        String finalUrlString = urlStr.trim().toLowerCase().replaceAll("www.", "");
        Map<String, Object> response = new HashMap<>();
        try {
            final URL url = new URL(finalUrlString);
            Optional<Site> matchingSite = sites.getSites().stream()
                    .filter(site -> finalUrlString.contains(site.getUrl()))
                    .findFirst();

            if (matchingSite.isEmpty()) {
                log.error("This page is located on additional sites specified in the configuration file.");
                response.put("result", false);
                response.put("error", "This page is located on additional sites specified in the configuration file.");
                return response;
            }

            Site siteConfig = matchingSite.get();
            SiteEntity siteEntity = siteRepo.findByUrl(siteConfig.getUrl())
                    .orElseGet(() -> {
                        SiteEntity newSite = new SiteEntity(siteConfig.getUrl(), siteConfig.getName(), SiteStatusType.INDEXING);
                        siteRepo.save(newSite);
                        return newSite;
                    });

            siteEntity.setStatus(SiteStatusType.INDEXING);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepo.save(siteEntity);
            log.info("Indexing page '{}'", siteConfig.getUrl());
            webScraperService.reindexPage(url.getPath(), siteEntity);

            response.put("result", true);
        } catch (MalformedURLException e) {
            log.error("Wrong URL format");
            response.put("result", false);
            response.put("error", "Wrong URL format");
        } catch (Exception e) {
            log.error("Error processing page: \" + e.getMessage()");
            response.put("result", false);
            response.put("error", "Error processing page: " + e.getMessage());
        }

        return response;
    }
}
