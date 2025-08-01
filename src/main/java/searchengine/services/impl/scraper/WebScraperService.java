package searchengine.services.impl.scraper;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.UnexpectedRollbackException;
import searchengine.models.LemmaEntity;
import searchengine.models.PageEntity;
import searchengine.models.SiteEntity;
import searchengine.repos.PageRepository;
import searchengine.repos.SiteRepository;
import searchengine.services.impl.textWorkers.TextLemmaParser;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Log4j2
public class WebScraperService {
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    @Autowired
    SiteIndexingImpl siteIndexingImpl;

    private Connection.Response fetchDocument(String path, SiteEntity siteEntity) {
        String url = getFullUrl(path, siteEntity);
        log.info("Fetch document by url \"{}\"", url);
        try {
            return Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .referrer("https://lenta.ru/")
                    .timeout(15000)
                    .followRedirects(true)
                    .execute();
        } catch (IOException ex) {
            saveUrlPage(path, ex.getMessage(), 500, siteEntity);
            log.error(ex.getMessage());
        }
        return null;
    }

    public Map<PageEntity, Map<LemmaEntity, Integer>> getPageAndSave(String path, SiteEntity siteEntity) {
        try {
            Connection.Response response = fetchDocument(path, siteEntity);
            if (response != null) {
                Document document = response.parse();
                int statusCode = response.statusCode();
                return saveUrlPage(path, document.wholeText(), statusCode, siteEntity); // Сохраняем страницу с кодом 200
            }
        } catch (HttpStatusException e) {
            log.error("HTTP error while fetching URL: {}, Status: {}", path, e.getStatusCode());
            saveUrlPage(path, e.getMessage(), e.getStatusCode(), siteEntity);
            throw new RuntimeException("HTTP error fetching URL: " + path, e);
        } catch (IOException e) {
            log.error("IO error while fetching URL: {}", path, e);
            saveUrlPage(path, e.getMessage(), 500, siteEntity);
            throw new RuntimeException("IO error fetching URL: " + path, e);
        }
        return null;
    }

    @SneakyThrows
    private Map<PageEntity, Map<LemmaEntity, Integer>> saveUrlPage(String checkingUrl, String pageContent, int statusCode, SiteEntity siteEntity) {
        if (isNotContainsUrl(checkingUrl, siteEntity)) {
            PageEntity page = new PageEntity(siteEntity, checkingUrl);
            page.setContent(pageContent);
            page.setCode(statusCode);
            siteEntity.setStatusTime(LocalDateTime.now());
            log.info("Saving url \"{}\"", checkingUrl);
            pageRepo.save(page);
            siteRepo.save(siteEntity);
            try {
                if (statusCode == 200) {
                    Map<LemmaEntity, Integer> lemmaAndCount = siteIndexingImpl.saveTextToLemmasAndIndexes(pageContent, siteEntity, page);
                    return lemmaAndCount.isEmpty() ? null : Map.of(page, lemmaAndCount);
                }
            } catch (UnexpectedRollbackException ure) {
                log.warn("UnexpectedRollbackException — caching lemma batch for page {}", page.getId());
                return Map.of(page, siteIndexingImpl.getLemmasAndCountWithKey(
                        new TextLemmaParser().sortWordsOnRussianAndEnglishWords(pageContent),
                        siteEntity
                ));
            }
        }
        return null;
    }

    public void finalizeFailedLemmaBatches(
            Map<PageEntity, Map<LemmaEntity, Integer>> errorLemmasTransaction,
            SiteEntity siteEntity
    ) {
        for (Map.Entry<PageEntity, Map<LemmaEntity, Integer>> entry : errorLemmasTransaction.entrySet()) {
            PageEntity pageEntity = entry.getKey();
            Map<LemmaEntity, Integer> lemmaAndCount = entry.getValue();

            if (!lemmaAndCount.isEmpty()) {
                try {
                    siteIndexingImpl.addOrUpdateLemmas(lemmaAndCount, siteEntity, pageEntity);
                } catch (Exception e) {
                    log.error("Error finalizing batch for page {}: {}", pageEntity.getId(), e.getMessage());
                }
            }
        }
    }

    public boolean isNotContainsUrl(String path, SiteEntity siteEntity) {
        return pageRepo.findByPathAndSiteEntity(path, siteEntity) == null;
    }

    public void reindexPage(String path, SiteEntity siteEntity) {
        PageEntity existingPage = pageRepo.findByPath(path);
        siteIndexingImpl.removePageData(existingPage);
        pageRepo.delete(existingPage);
        getPageAndSave(path, siteEntity);
    }

    private String getFullUrl(String path, SiteEntity siteEntity) {
        return siteEntity.getUrl() + path;
    }
}

