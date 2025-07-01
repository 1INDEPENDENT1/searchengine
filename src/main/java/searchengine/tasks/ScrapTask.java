package searchengine.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import searchengine.models.SiteEntity;
import searchengine.models.SiteStatusType;
import searchengine.repos.PageRepository;
import searchengine.repos.SiteRepository;
import searchengine.services.HtmlParser;
import searchengine.services.WebScraperService;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
@Log4j2
public class ScrapTask extends RecursiveAction {
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    private final SiteEntity siteEntity;
    private final WebScraperService webScraperService;
    private final String url;
    private final boolean isRootTask;
    private final AtomicInteger totalTaskCount;
    private final AtomicInteger completedTaskCount;
    private final Semaphore taskSemaphore;
    private final ForkJoinPool pool;

    public static final int MAX_CONCURRENT_TASKS = 12_000;

    @Override
    protected void compute() {
        try {
            taskSemaphore.acquire();
            totalTaskCount.incrementAndGet();
            webScraperService.getPageAndSave(url, siteEntity);
            Set<String> discoveredUrls = new HashSet<>(new HtmlParser(url, siteEntity).getPaths());
            processDiscoveredUrls(discoveredUrls);
        } catch (Exception e) {
            log.error("Error processing URL", e);
        } finally {
            completedTaskCount.incrementAndGet();
            taskSemaphore.release();

            if (isRootTask) {
                endProcessing();
            }
        }
    }

    private void processDiscoveredUrls(Set<String> urls) {
        List<ScrapTask> tasks = urls.stream()
                .filter(webScraperService::isNotContainsUrl)
                .map(u -> new ScrapTask(siteRepo, pageRepo, siteEntity, webScraperService, u,
                        false, totalTaskCount, completedTaskCount, taskSemaphore, pool))
                .toList();
        invokeAll(tasks);
    }

    private void endProcessing() {
        if (completedTaskCount.get() == totalTaskCount.get()) {
            synchronized (this) {
                if (!pool.isShutdown()) {
                    pool.shutdown();
                    log.info("All tasks completed.");
                    siteEntity.setStatus(SiteStatusType.INDEXED);
                    siteRepo.save(siteEntity);
                }
            }
        }
    }
}

