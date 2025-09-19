package searchengine.services.impl.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.models.SiteEntity;
import searchengine.models.SiteStatusType;
import searchengine.repos.SiteRepository;
import searchengine.services.IndexingService;
import searchengine.services.impl.scraper.SiteIndexingImpl;
import searchengine.services.impl.scraper.ScrapTask;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Log4j2
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sites;
    private final SiteRepository siteRepo;
    private final SiteIndexingImpl siteIndexingImpl;
    private final SiteIndexingHelper siteIndexingHelper;
    private ForkJoinPool sharedPool;
    private final AtomicInteger activeTaskCount = new AtomicInteger(0);
    private final GatesConfig gatesConfig;

    @Override
    public boolean startIndexing() {
        if (!siteIndexingHelper.isIndexingInProgress()) {
            // Пересоздание пула при необходимости
            if (sharedPool == null || sharedPool.isShutdown() || sharedPool.isTerminated()) {
                sharedPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
                log.info("Created new shared ForkJoinPool");
            }

            log.info("Starting indexing process.");
            List<SiteEntity> entities = updateOrCreateSiteEntities(sites.getSites());

            for (SiteEntity siteEntity : entities) {
                ScrapTask task = siteIndexingHelper.prepareIndexingTask(siteEntity, activeTaskCount);
                sharedPool.submit(task);
            }

            gatesConfig.indexingGate().start();
            log.info("Indexing started asynchronously for all sites.");
            return true;
        }

        log.info("Indexing has already started. New tasks have not started.");
        return false;
    }

    @Override
    public boolean stopIndexing() {
        if (siteIndexingHelper.isIndexingInProgress()) {
            log.info("Stopping shared pool...");

            sharedPool.shutdownNow();

            log.info("Waiting for all tasks to finish...");
            int attempts = 0;
            while (activeTaskCount.get() > 0 && attempts < 10) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted while waiting for task completion", e);
                    break;
                }
                attempts++;
            }

            log.info("All tasks reported finished or timeout reached");

            List<SiteEntity> indexingSites = siteRepo.findAll().stream()
                    .filter(site -> site.getStatus() == SiteStatusType.INDEXING)
                    .toList();

            for (SiteEntity site : indexingSites) {
                siteIndexingHelper.setManualStopStatus(site);
            }

            log.info("Set FAILED status to all indexing sites");

            gatesConfig.indexingGate().stop();
            return true;
        }
        return false;
    }

    private List<SiteEntity> updateOrCreateSiteEntities(List<Site> sitesList) {
        log.info("Clearing database before indexing...");
        siteIndexingHelper.clearDatabase();

        List<SiteEntity> newEntities = new ArrayList<>();
        sitesList.forEach(site -> {
            SiteEntity entity = new SiteEntity(site.getUrl(), site.getName(), SiteStatusType.INDEXING);
            siteRepo.save(entity);
            newEntities.add(entity);
        });

        return newEntities;
    }


    public Map<String, Object> handlePageUpdate(String urlStr) {
        String finalUrlString = urlStr.trim().toLowerCase().replaceAll("www.", "");
        Map<String, Object> response = new HashMap<>();
        try {
            URL url = new URL(finalUrlString);
            Optional<Site> matchingSite = sites.getSites().stream()
                    .filter(site -> finalUrlString.contains(site.getUrl()))
                    .findFirst();

            if (matchingSite.isEmpty()) {
                String error = "This page is located on additional sites specified in the configuration file.";
                log.error(error);
                response.put("result", false);
                response.put("error", error);
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
            siteIndexingImpl.reindexPage(url.getPath(), siteEntity);

            response.put("result", true);
        } catch (MalformedURLException e) {
            log.error("Wrong URL format");
            response.put("result", false);
            response.put("error", "Wrong URL format");
        } catch (Exception e) {
            log.error("Error processing page: {}", e.getMessage());
            response.put("result", false);
            response.put("error", "Error processing page: " + e.getMessage());
        }

        return response;
    }
}
