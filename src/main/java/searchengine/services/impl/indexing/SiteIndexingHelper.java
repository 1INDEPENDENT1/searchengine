package searchengine.services.impl.indexing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.models.SiteEntity;
import searchengine.models.SiteStatusType;
import searchengine.repos.DbCleanerRepository;
import searchengine.repos.PageRepository;
import searchengine.repos.SiteRepository;
import searchengine.services.impl.scraper.ScrapTask;
import searchengine.services.impl.scraper.SiteIndexingImpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class SiteIndexingHelper {
    private final PageRepository pageRepo;
    private final SiteRepository siteRepo;
    private final SiteIndexingImpl siteIndexingImpl;
    private final DbCleanerRepository dbCleaner;
    private final GatesConfig gatesConfig;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearDatabase() {
        dbCleaner.truncateAll();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<SiteEntity> createSites(List<Site> sitesList) {
        List<SiteEntity> newEntities = new ArrayList<>();
        sitesList.forEach(site -> {
            SiteEntity entity = new SiteEntity(site.getUrl(), site.getName(), SiteStatusType.INDEXING);
            siteRepo.save(entity);
            newEntities.add(entity);
        });

        return newEntities;
    }

    @Transactional
    public ScrapTask prepareIndexingTask(SiteEntity site, AtomicInteger activeTaskCount) {
        site.setStatusTime(LocalDateTime.now());
        site.setStatus(SiteStatusType.INDEXING);
        siteRepo.save(site);
        return new ScrapTask(
                siteRepo,
                pageRepo,
                site,
                siteIndexingImpl,
                "",
                true,
                ConcurrentHashMap.newKeySet(),
                new ConcurrentHashMap<>(),
                activeTaskCount,
                gatesConfig
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
                .anyMatch(site -> site.getStatus() == SiteStatusType.INDEXING) && gatesConfig.indexingGate().isRunning();
    }
}
