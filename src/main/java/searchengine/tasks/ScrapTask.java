package searchengine.tasks;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import searchengine.models.PageEntity;
import searchengine.models.SiteEntity;
import searchengine.models.SiteStatusType;
import searchengine.repos.PageRepository;
import searchengine.repos.SiteRepository;
import searchengine.services.HtmlParser;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
@AllArgsConstructor
@Log4j2
public class ScrapTask extends RecursiveAction {
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    /*    private final LemmaRepository lemmaRepo;
        private final IndexesRepository indexRepo;*/
    private final SiteEntity siteEntity;
    private String url;
    private boolean isRootTask;
    private final AtomicInteger totalTaskCount = new AtomicInteger();
    private final AtomicInteger completedTaskCount = new AtomicInteger();

    @Override
    protected void compute() {
        try {
            totalTaskCount.incrementAndGet();
            fetchDocument(url);
            Set<String> discoveredUrls = new HashSet<>(new HtmlParser(url).getUrls());
            processDiscoveredUrls(discoveredUrls);
        } catch (Exception e) {
            log.error("Error processing URL", e);
        } finally {
            completedTaskCount.incrementAndGet();
            if (isRootTask) {
                endProcessingNgetStrings();
            }
        }
    }

    private void fetchDocument(String url) {
        log.info("Fetch document by url \"{}\"", url);
        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .referrer("https://lenta.ru/")
                    .timeout(24000)
                    .followRedirects(true)
                    .execute();

            int statusCode = response.statusCode(); // Получаем статус HTTP
            Document document = response.parse();  // Парсим документ

            saveUrlPage(url, document.wholeText(), statusCode); // Сохраняем страницу с кодом 200
        } catch (HttpStatusException e) {
            log.error("HTTP error while fetching URL: {}, Status: {}", url, e.getStatusCode());
            saveUrlPage(url, e.getMessage(), e.getStatusCode());
            throw new RuntimeException("HTTP error fetching URL: " + url, e);
        } catch (IOException e) {
            log.error("IO error while fetching URL: {}", url, e);
            saveUrlPage(url, getMessage(e), 500);
            throw new RuntimeException("IO error fetching URL: " + url, e);
        }
    }

    private static String getMessage(IOException e) {
        return e.getMessage();
    }

    private void processDiscoveredUrls(Set<String> urls) {
        List<ScrapTask> tasks = urls.stream()
                .filter(this::isNotContainsUrl)
                .map(url -> new ScrapTask(siteRepo, pageRepo, siteEntity, url, false))
                .toList();
        invokeAll(tasks);
    }

    private void saveUrlPage(String checkingUrl, String pageContent, int statusCode) {
        if (isNotContainsUrl(checkingUrl)) {
            PageEntity page = new PageEntity(siteEntity, checkingUrl);
            page.setContent(pageContent);
            page.setCode(statusCode);
            siteEntity.setStatusTime(LocalDateTime.now());
            pageRepo.save(page);
            siteRepo.save(siteEntity);
        }
    }

    private boolean isNotContainsUrl(String url) {
        return pageRepo.findByPath(url) == null;
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
