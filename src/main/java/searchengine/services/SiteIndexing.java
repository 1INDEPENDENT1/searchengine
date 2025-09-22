package searchengine.services;

import searchengine.models.LemmaEntity;
import searchengine.models.PageEntity;
import searchengine.models.SiteEntity;

import java.util.Map;

public interface SiteIndexing {
    void getPageAndSave(String path, SiteEntity siteEntity);

    void saveUrlPage(String checkingUrl, String pageContent, int statusCode, SiteEntity siteEntity);

    void reindexPage(String path, SiteEntity siteEntity);
}
