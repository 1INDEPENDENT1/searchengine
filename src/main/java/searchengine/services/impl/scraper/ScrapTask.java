package searchengine.services.impl.scraper;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import searchengine.models.LemmaEntity;
import searchengine.models.PageEntity;
import searchengine.models.SiteEntity;
import searchengine.models.SiteStatusType;
import searchengine.repos.PageRepository;
import searchengine.repos.SiteRepository;

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
    private final Set<String> visitedPath;
    private final Map<PageEntity, Map<LemmaEntity, Integer>> errorLemmasTransaction;

    public static final int MAX_CONCURRENT_TASKS = 12_000;

    @Override
    protected void compute() {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        try {
            HtmlParser htmlParser = new HtmlParser(url, siteEntity);
            taskSemaphore.acquire();
            totalTaskCount.incrementAndGet();
            Map<PageEntity, Map<LemmaEntity, Integer>> tempLemmasTransactions = webScraperService.getPageAndSave(url, siteEntity);
            if (tempLemmasTransactions != null) {
                errorLemmasTransaction.putAll(tempLemmasTransactions);
            }
            visitedPath.add(url);
            Set<String> discoveredUrls = htmlParser.getPaths();
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
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        List<ScrapTask> tasks = urls.stream()
                .filter(visitedPath::add)
                .map(url -> new ScrapTask(siteRepo,
                        pageRepo,
                        siteEntity,
                        webScraperService,
                        url,
                        false,
                        totalTaskCount,
                        completedTaskCount,
                        taskSemaphore,
                        pool,
                        visitedPath,
                        errorLemmasTransaction)).toList();
        try {
            invokeAll(tasks);
        } catch (CancellationException e) {
            log.debug("invokeAll cancelled for URL: {}", url);
        } catch (Exception e) {
            log.error("Unexpected error in invokeAll", e);
        }
    }

    private void endProcessing() {
        if (completedTaskCount.get() == totalTaskCount.get()) {
            synchronized (this) {
                if (!pool.isShutdown()) {
                    if (!errorLemmasTransaction.isEmpty()) {
                        log.info("Finalizing {} failed lemma batches for site: {}", errorLemmasTransaction.size(), siteEntity.getUrl());
                        webScraperService.finalizeFailedLemmaBatches(errorLemmasTransaction, siteEntity);
                    }

                    pool.shutdown();
                    log.info("All tasks completed.");
                    siteEntity.setStatus(SiteStatusType.INDEXED);
                    siteRepo.save(siteEntity);
                }
            }
        }
    }
}

