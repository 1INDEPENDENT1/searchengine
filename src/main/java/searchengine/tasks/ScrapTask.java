package searchengine.tasks;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
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
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@AllArgsConstructor
@Log4j2
public class ScrapTask extends RecursiveTask<ConcurrentSkipListSet<String>> {
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    /*    private final LemmaRepository lemmaRepo;
        private final IndexesRepository indexRepo;*/
    private final SiteEntity siteEntity;
    private String url;
    private ConcurrentSkipListSet<String> keys;
    /*    private volatile String firstUrl;*/

    @Override
    protected ConcurrentSkipListSet<String> compute() {
        Document document;
        try {
            document = fetchDocument(url);
            saveUrlPage(url, document);
            Set<String> discoveredUrls = new HashSet<>(new HtmlParser(url).getUrls());
            processDiscoveredUrls(discoveredUrls);
        } catch (Exception e) {
            log.error("Error processing URL", e);
        }
        endProcessingNgetStrings();
        return keys;
    }

    private Document fetchDocument(String url) throws IOException {
        log.info("Fetch document by url \"{}\"", url);
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .referrer("https://lenta.ru/")
                .timeout(24000)
                .followRedirects(true)
                .get();
    }


    private void processDiscoveredUrls(Set<String> urls) {
        List<ScrapTask> tasks = urls.stream()
                .filter(url -> !keys.contains(url))
                .map(url -> new ScrapTask(siteRepo, pageRepo, siteEntity, url, keys))
                .collect(Collectors.toList());
        invokeAll(tasks);
    }

    private void saveUrlPage(String checkingUrl, Document document) {
        PageEntity page = new PageEntity(siteEntity, checkingUrl);
        page.setContent(document.wholeText());
        page.setCode(200);
        siteEntity.setStatusTime(LocalDateTime.now());
        pageRepo.save(page);
        keys.add(url);
    }

    private void endProcessingNgetStrings() {
        if (ForkJoinTask.getPool().getActiveThreadCount() == 1) {
            ForkJoinTask.getPool().shutdown();

            log.info("ForkJoinTask.getPool().shutdown() and return keys");
            siteEntity.setStatus(SiteStatusType.INDEXED);
            siteRepo.save(siteEntity);
        }
    }
}
