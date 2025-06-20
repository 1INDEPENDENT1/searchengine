package searchengine.tasks;

import lombok.AllArgsConstructor;
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
@AllArgsConstructor
@Log4j2
public class ScrapTask extends RecursiveAction {
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    private final SiteEntity siteEntity;
    private final WebScraperService webScraperService;
    private String url;
    private boolean isRootTask;
    private final AtomicInteger totalTaskCount = new AtomicInteger();
    private final AtomicInteger completedTaskCount = new AtomicInteger();

    private static final int MAX_CONCURRENT_TASKS = 12_000;
    private static final Semaphore taskSemaphore = new Semaphore(MAX_CONCURRENT_TASKS);

    @Override
    protected void compute() {
        try {
            taskSemaphore.acquire(); // Ждём, если лимит достигнут
            totalTaskCount.incrementAndGet();
            webScraperService.getPageAndSave(url, siteEntity);
            Set<String> discoveredUrls = new HashSet<>(new HtmlParser(url).getPaths());
            processDiscoveredUrls(discoveredUrls);
        } catch (Exception e) {
            log.error("Error processing URL", e);
        }
        finally {
            completedTaskCount.incrementAndGet();
            taskSemaphore.release(); // Освобождаем слот

            if (isRootTask) {
                endProcessingNgetStrings();
            }
        }
    }

    private void processDiscoveredUrls(Set<String> urls) {
        List<ScrapTask> tasks = urls.stream()
                .filter(webScraperService::isNotContainsUrl)
                .map(url -> new ScrapTask(siteRepo, pageRepo, siteEntity, webScraperService, url, false))
                .toList();
        invokeAll(tasks);
    }

    private void endProcessingNgetStrings() {
        if (completedTaskCount.get() == totalTaskCount.get()) {
            synchronized (this) {
                if (!ForkJoinTask.getPool().isShutdown()) {
                    ForkJoinTask.getPool().shutdown();
                    log.info("All tasks completed.");
                    siteEntity.setStatus(SiteStatusType.INDEXED);
                    siteRepo.save(siteEntity);
                }
            }
        }
    }
}

