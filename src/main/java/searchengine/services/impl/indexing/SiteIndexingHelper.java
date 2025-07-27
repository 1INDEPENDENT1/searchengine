package searchengine.services.impl.indexing;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.models.SiteEntity;
import searchengine.models.SiteStatusType;
import searchengine.repos.PageRepository;
import searchengine.repos.SiteRepository;
import searchengine.services.impl.scraper.ScrapTask;
import searchengine.services.impl.scraper.WebScraperService;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
@Service
public class SiteIndexingHelper {
    @PersistenceContext
    private EntityManager entityManager;
    private final PageRepository pageRepo;
    private final SiteRepository siteRepo;
    private final WebScraperService webScraperService;
    private final DbCleaner dbCleaner;

    @Transactional
    public void clearDatabase() {
        dbCleaner.truncateAll();
        entityManager.clear();
    }

    @Transactional
    public ScrapTask prepareIndexingTask(SiteEntity site, ForkJoinPool pool, AtomicInteger activeTaskCount) {
        site.setStatusTime(LocalDateTime.now());
        site.setStatus(SiteStatusType.INDEXING);
        siteRepo.save(site);
        return new ScrapTask(
                siteRepo,
                pageRepo,
                site,
                webScraperService,
                "",
                true,
                pool,
                ConcurrentHashMap.newKeySet(),
                new ConcurrentHashMap<>(),
                activeTaskCount
        );
    }

    @Transactional
    public void setManualStopStatus(SiteEntity site) {
        site.setStatus(SiteStatusType.FAILED);
        site.setStatusTime(LocalDateTime.now());
        site.setLastError("Индексация была остановлена вручную");
        siteRepo.save(site);
    }

    public boolean isIndexingInProgress() {
        return siteRepo.findAll().stream()
                .anyMatch(site -> site.getStatus() == SiteStatusType.INDEXING);
    }
}
