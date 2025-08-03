package searchengine.services;

import searchengine.models.LemmaEntity;
import searchengine.models.PageEntity;
import searchengine.models.SiteEntity;

import java.util.Map;

public interface SiteIndexing {
    Map<PageEntity, Map<LemmaEntity, Integer>> getPageAndSave(String path, SiteEntity siteEntity);

    Map<PageEntity, Map<LemmaEntity, Integer>> saveUrlPage(String checkingUrl, String pageContent, int statusCode, SiteEntity siteEntity);

    void finalizeFailedLemmaBatches(Map<PageEntity, Map<LemmaEntity, Integer>> errorLemmasTransaction, SiteEntity siteEntity);

    void reindexPage(String path, SiteEntity siteEntity);
}
