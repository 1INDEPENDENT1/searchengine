package searchengine.services.impl;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.models.LemmaEntity;
import searchengine.models.SiteEntity;
import searchengine.models.SiteStatusType;
import searchengine.repos.IndexesRepository;
import searchengine.repos.LemmaRepository;
import searchengine.repos.PageRepository;
import searchengine.repos.SiteRepository;
import searchengine.services.IndexingService;
import searchengine.services.WebScraperService;
import searchengine.tasks.ScrapTask;

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
    private final PageRepository pageRepo;
    private final WebScraperService webScraperService;
    private final ForkJoinPool forkJoinPool;
    private final LemmaRepository lemmaRepo;
    private final IndexesRepository indexRepo;


    @Autowired
    public IndexingServiceImpl(SitesList sites, SiteRepository siteRepo, PageRepository pageRepo, WebScraperService webScraperService, LemmaRepository lemmaRepo, IndexesRepository indexRepo) {
        this.sites = sites;
        this.siteRepo = siteRepo;
        this.pageRepo = pageRepo;
        this.webScraperService = webScraperService;
        this.forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        this.indexRepo = indexRepo;
        this.lemmaRepo = lemmaRepo;
    }

    @Override
    public boolean startIndexing() {
        if (!isIndexingInProgress()) {
            log.info("Starting indexing process.");
            List<Site> sitesList = sites.getSites();
            List<SiteEntity> entities = updateOrCreateSiteEntities(sitesList);

            List<SiteEntity> notIndexedEntities = entities.stream()
                    .filter(siteEntity -> !siteEntity.getStatus().equals(SiteStatusType.INDEXED))
                    .toList();

            if (notIndexedEntities.isEmpty()) {
                log.info("Нет сайтов для индексации — все уже INDEXED.");
                return false;
            }

            for (SiteEntity siteEntity : notIndexedEntities) {
                ScrapTask task = prepareIndexingTask(siteEntity);
                forkJoinPool.submit(task);
            }

            log.info("Indexing started asynchronously for all sites.");
            return true;
        }

        log.info("Indexing has already started. New tasks have not started.");
        return false;
    }

    private List<SiteEntity> updateOrCreateSiteEntities(final List<Site> sitesList) {
        final List<SiteEntity> entities = siteRepo.findAll();
        Map<String, SiteEntity> urlToEntityMap = entities.stream()
                .collect(Collectors.toMap(SiteEntity::getUrl, Function.identity()));

        sitesList.forEach(site -> urlToEntityMap.computeIfAbsent(site.getUrl(), url -> {
            SiteEntity newEntity = createNewSiteEntity(site);
            entities.add(newEntity);
            return newEntity;
        }));

        return entities;
    }

    private SiteEntity createNewSiteEntity(final Site site) {
        final SiteEntity entity = new SiteEntity(site.getUrl(), site.getName(), SiteStatusType.INDEXING);
        siteRepo.save(entity);
        return entity;
    }

    public ScrapTask prepareIndexingTask(SiteEntity site) {
        site.setStatusTime(LocalDateTime.now());
        site.setStatus(SiteStatusType.INDEXING);
        siteRepo.save(site);
        clearExistingData(site);
        return new ScrapTask(siteRepo, pageRepo, site, webScraperService, site.getUrl(), true);
    }

    private void clearExistingData(SiteEntity site) {
        pageRepo.deleteBySiteEntity(site);
        List<LemmaEntity> lemmas = lemmaRepo.findBySiteEntity(site);
        indexRepo.deleteByLemmaEntityIn(lemmas);
        lemmaRepo.deleteBySiteEntity(site);
    }

    public boolean isIndexingInProgress() {
        return siteRepo.findAll().stream()
                .anyMatch(site -> site.getStatus() == SiteStatusType.INDEXING);
    }

    @Override
    public boolean stopIndexing() {
        if (isIndexingInProgress()) {
            forkJoinPool.shutdownNow();

            List<SiteEntity> indexingSites = siteRepo.findAll().stream()
                    .filter(site -> site.getStatus() == SiteStatusType.INDEXING)
                    .toList();

            for (SiteEntity site : indexingSites) {
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
            new URL(finalUrlString);
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
            webScraperService.reindexPage(finalUrlString, siteEntity);

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
