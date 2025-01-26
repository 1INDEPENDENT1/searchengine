package searchengine.services.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.models.IndexesEntity;
import searchengine.models.SiteEntity;
import searchengine.models.SiteStatusType;
import searchengine.repos.IndexesRepository;
import searchengine.repos.LemmaRepository;
import searchengine.repos.PageRepository;
import searchengine.repos.SiteRepository;
import searchengine.services.IndexingService;
import lombok.extern.log4j.Log4j2;
import searchengine.tasks.ScrapTask;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Log4j2
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sites;
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
/*    private final LemmaRepository lemmaRepo;
    private final IndexesRepository indexRepo;*/
    private final ForkJoinPool forkJoinPool;
    private final static AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    @Autowired
    public IndexingServiceImpl(SitesList sites, SiteRepository siteRepo, PageRepository pageRepo/*, LemmaRepository lemmaRepo, IndexesRepository indexRepo*/) {
        this.sites = sites;
        this.siteRepo = siteRepo;
        this.pageRepo = pageRepo;
/*        this.lemmaRepo = lemmaRepo;
        this.indexRepo = indexRepo;*/
        this.forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
    }

    @Override
    public boolean startIndexing() {
        log.info("Starting indexing process.");
        List<Site> sitesList = sites.getSites();
        List<SiteEntity> entities = updateOrCreateSiteEntities(sitesList);

        List<SiteEntity> notIndexedEntities = entities.parallelStream()
                .filter(siteEntity -> !siteEntity.getStatus().equals(SiteStatusType.INDEXED)).toList();

        if (!notIndexedEntities.isEmpty() || !isRunning.get()) {
            isRunning.set(true);
            notIndexedEntities.parallelStream()
                    .filter(siteEntity -> !siteEntity.getStatus().equals(SiteStatusType.INDEXED))
                    .forEach(siteEntity -> CompletableFuture.runAsync(() -> {
                        ForkJoinPool customPool = new ForkJoinPool(); // Создаем отдельный пул для каждой задачи
                        try {
                            ScrapTask task = new ScrapTask(siteRepo, pageRepo, siteEntity, siteEntity.getUrl(), true);
                            customPool.invoke(task);
                        } finally {
                            customPool.shutdown();
                        }
                    }));

            log.info("Indexing completed for all sites.");
            return true;
        }

        log.info("All sites have already been indexed or are currently being indexed.");
        return false;
    }

    private List<SiteEntity> updateOrCreateSiteEntities(final List<Site> sitesList) {
        final List<SiteEntity> entities = siteRepo.findAll();
        Map<String, SiteEntity> urlToEntityMap = entities.stream()
                .collect(Collectors.toMap(SiteEntity::getUrl, Function.identity()));

        sitesList.forEach(site -> {
            urlToEntityMap.computeIfAbsent(site.getUrl(), url -> {
                SiteEntity newEntity = createNewSiteEntity(site);
                entities.add(newEntity);
                return newEntity;
            });
        });

        return entities;
    }

    private SiteEntity createNewSiteEntity(final Site site) {
        final SiteEntity entity = new SiteEntity(site.getUrl(), site.getName(), SiteStatusType.INDEXING);
        siteRepo.save(entity);
        return entity;
    }

    private void indexSite(SiteEntity site) {
        site.setStatusTime(LocalDateTime.now());
        site.setStatus(SiteStatusType.INDEXING);
        siteRepo.save(site);
        clearExistingData(site);
        ArrayList<String> listOfUrls = new ArrayList<>(List.of(site.getUrl()));
        listOfUrls.parallelStream().forEach(url -> {
            ScrapTask task = new ScrapTask(siteRepo, pageRepo, site, url, true);
            ForkJoinPool.commonPool().invoke(task);
        });
    }

    private void clearExistingData(SiteEntity site) {
        /*List<IndexesEntity> indexes = indexRepo.findIndex4LemmaNPage(site.getUrl());
        indexRepo.deleteAll(indexes);*/
        pageRepo.deleteBySiteEntity(site);
    }

    @Override
    public boolean stopIndexing() {
        if (!forkJoinPool.isShutdown()) {
            forkJoinPool.shutdownNow();
            return true;
        }
        return false;
    }

    public boolean addUpdatePage(String page) {
        try {
            URL url = new URL(URLDecoder.decode(page));
            url.toURI();
            return handlePageUpdate(url);
        } catch (MalformedURLException | URISyntaxException e) {
            log.error("Invalid URL: {}", page, e);
            return false;
        }
    }

    private boolean handlePageUpdate(URL url) throws URISyntaxException {
        SiteEntity site = siteRepo.findByUrl(url.toString()).orElse(
                new SiteEntity(url.toURI().toString(), url.getHost(), SiteStatusType.INDEXING));
        site.setStatusTime(LocalDateTime.now());
        site.setStatus(SiteStatusType.INDEXING);
        siteRepo.save(site);
        return true;
    }
}
