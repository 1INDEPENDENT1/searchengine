package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.models.LemmaEntity;
import searchengine.models.SiteEntity;
import searchengine.models.SiteStatusType;
import searchengine.repos.IndexesRepository;
import searchengine.repos.LemmaRepository;
import searchengine.repos.PageRepository;
import searchengine.repos.SiteRepository;
import searchengine.services.tasks.ScrapTask;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
@Service
public class SiteIndexingHelper {
    private final LemmaRepository lemmaRepo;
    private final IndexesRepository indexRepo;
    private final PageRepository pageRepo;
    private final SiteRepository siteRepo;
    private final WebScraperService webScraperService;

    @Transactional
    public ScrapTask prepareIndexingTask(SiteEntity site, ForkJoinPool pool) {
        site.setStatusTime(LocalDateTime.now());
        site.setStatus(SiteStatusType.INDEXING);
        siteRepo.save(site);
        AtomicInteger totalCount = new AtomicInteger();
        AtomicInteger completedCount = new AtomicInteger();
        Semaphore semaphore = new Semaphore(ScrapTask.MAX_CONCURRENT_TASKS);
        return new ScrapTask(siteRepo,
                pageRepo,
                site,
                webScraperService,
                "",
                true,
                totalCount,
                completedCount,
                semaphore,
                pool,
                ConcurrentHashMap.newKeySet(),
                new ConcurrentHashMap<>());
    }

    @Transactional
    public void clearExistingData(SiteEntity site) {
        List<LemmaEntity> lemmas = lemmaRepo.findBySiteEntity(site);
        indexRepo.deleteByLemmaEntityIn(lemmas);
        pageRepo.deleteBySiteEntity(site);
        lemmaRepo.deleteBySiteEntity(site);
        siteRepo.delete(site);
    }

    public boolean isIndexingInProgress() {
        return siteRepo.findAll().stream()
                .anyMatch(site -> site.getStatus() == SiteStatusType.INDEXING);
    }
}
