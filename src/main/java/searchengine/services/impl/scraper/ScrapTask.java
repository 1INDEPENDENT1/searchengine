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
import java.util.concurrent.CancellationException;
import java.util.concurrent.RecursiveAction;
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
    private final Set<String> visitedPath;
    private final Map<PageEntity, Map<LemmaEntity, Integer>> errorLemmasTransaction;
    private final AtomicInteger activeTaskCount;

    @Override
    protected void compute() {
        if (Thread.currentThread().isInterrupted()) return;
        activeTaskCount.incrementAndGet();

        try {
            log.debug("Task started for URL: {}", url);

            visitedPath.add(url);
            HtmlParser htmlParser = new HtmlParser(url, siteEntity);
            Set<String> discoveredUrls = htmlParser.getPaths();

            processDiscoveredUrls(discoveredUrls);

            Map<PageEntity, Map<LemmaEntity, Integer>> tempLemmasTransactions =
                    webScraperService.getPageAndSave(url, siteEntity);

            if (tempLemmasTransactions != null) {
                errorLemmasTransaction.putAll(tempLemmasTransactions);
            }

            log.debug("Task completed for URL: {}", url);
        } catch (Exception e) {
            log.error("Error processing URL: {}", url, e);
        } finally {
            activeTaskCount.decrementAndGet();
            if (isRootTask) {
                log.info("Finishing processing for site: {}", siteEntity.getName());
                endProcessing();
            }
        }
    }

    private void processDiscoveredUrls(Set<String> urls) {
        if (Thread.currentThread().isInterrupted()) return;

        List<ScrapTask> tasks = urls.stream()
                .filter(visitedPath::add)
                .map(childUrl -> new ScrapTask(
                        siteRepo,
                        pageRepo,
                        siteEntity,
                        webScraperService,
                        childUrl,
                        false,
                        visitedPath,
                        errorLemmasTransaction,
                        activeTaskCount
                ))
                .toList();

        try {
            invokeAll(tasks);
        } catch (CancellationException e) {
            log.debug("invokeAll cancelled for URL: {}", url);
        } catch (Exception e) {
            log.error("Unexpected error in invokeAll for URL: {}", url, e);
        }
    }

    private void endProcessing() {
        synchronized (this) {
            if (!errorLemmasTransaction.isEmpty()) {
                log.info("Finalizing {} failed lemma batches for site: {}", errorLemmasTransaction.size(), siteEntity.getUrl());
                webScraperService.finalizeFailedLemmaBatches(errorLemmasTransaction, siteEntity);
            }

            log.info("All tasks completed for site: {}", siteEntity.getName());
            siteEntity.setStatus(SiteStatusType.INDEXED);
            siteRepo.save(siteEntity);
        }
    }
}
