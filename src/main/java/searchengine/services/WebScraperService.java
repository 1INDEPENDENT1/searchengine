package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.UnexpectedRollbackException;
import searchengine.models.LemmaEntity;
import searchengine.models.PageEntity;
import searchengine.repos.PageRepository;
import searchengine.repos.SiteRepository;


import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import searchengine.models.SiteEntity;
import searchengine.services.impl.SafeIndexingService;
import searchengine.services.impl.SiteIndexingImpl;

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

    private Map<PageEntity, Map<LemmaEntity, Integer>> saveUrlPage(String checkingUrl, String pageContent, int statusCode, SiteEntity siteEntity) {
        if (isNotContainsUrl(checkingUrl)) {
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
                    return Map.of(page, lemmaAndCount);
                }
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
        return null;
    }

    public boolean isNotContainsUrl(String path) {
        return pageRepo.findByPath(path) == null;
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

