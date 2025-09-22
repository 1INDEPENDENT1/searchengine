package searchengine.services.impl.scraper;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.models.PageEntity;
import searchengine.models.SiteEntity;
import searchengine.repos.PageRepository;
import searchengine.repos.SiteRepository;
import searchengine.services.SiteIndexing;
import searchengine.services.impl.textWorkers.TextLemmaParser;

import java.io.IOException;
import java.time.LocalDateTime;
@Service
@RequiredArgsConstructor
@Log4j2
public class SiteIndexingImpl implements SiteIndexing {
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    private final IndexAndLemmaDBWorker indexAndLemmaDBWorker;

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

    @Override
    public void getPageAndSave(String path, SiteEntity siteEntity) {
        try {
            Connection.Response response = fetchDocument(path, siteEntity);
            if (response != null) {
                Document document = response.parse();
                int statusCode = response.statusCode();
                saveUrlPage(path, document.wholeText(), statusCode, siteEntity); // Сохраняем страницу с кодом 200
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
    }

    @SneakyThrows
    @Override
    public void saveUrlPage(String checkingUrl, String pageContent, int statusCode, SiteEntity siteEntity) {
        if (isNotContainsUrl(checkingUrl, siteEntity)) {
            PageEntity page = new PageEntity(siteEntity, checkingUrl);
            page.setContent(pageContent);
            page.setCode(statusCode);
            siteEntity.setStatusTime(LocalDateTime.now());
            log.info("Saving url \"{}\"", checkingUrl);
            pageRepo.save(page);
            siteRepo.save(siteEntity);
            if (statusCode == 200) {
                indexAndLemmaDBWorker.saveTextToLemmasAndIndexes(pageContent, siteEntity, page);
            }
        }
    }

    @Override
    public void reindexPage(String path, SiteEntity siteEntity) {
        PageEntity existingPage = pageRepo.findByPath(path);
        indexAndLemmaDBWorker.removePageData(existingPage);
        pageRepo.delete(existingPage);
        getPageAndSave(path, siteEntity);
    }

    private boolean isNotContainsUrl(String path, SiteEntity siteEntity) {
        return pageRepo.findByPathAndSiteEntity(path, siteEntity) == null;
    }

    private String getFullUrl(String path, SiteEntity siteEntity) {
        return siteEntity.getUrl() + path;
    }
}

