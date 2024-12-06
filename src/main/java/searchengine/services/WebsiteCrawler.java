package searchengine.services;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import searchengine.config.SitesList;
import searchengine.repos.IndexesRepository;
import searchengine.repos.LemmaRepository;
import searchengine.repos.PageRepository;
import searchengine.repos.SiteRepository;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

@RequiredArgsConstructor
public class WebsiteCrawler extends RecursiveAction {
    @Override
    protected void compute() {

    }
/*
    private final SitesList sites;
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    private final LemmaRepository lemmaRepo;
    private final IndexesRepository indexRepo;
    private ForkJoinPool forkJoinPool;

    @Autowired
    public WebsiteCrawler(SitesList sites, SiteRepository siteRepo, PageRepository pageRepo, LemmaRepository lemmaRepo, IndexesRepository indexRepo) {
        this.sites = sites;
        this.siteRepo = siteRepo;
        this.pageRepo = pageRepo;
        this.lemmaRepo = lemmaRepo;
        this.indexRepo = indexRepo;
        this.forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
    }

    @Override
    public indexStatus startIndexing() {
        log.info("Starting indexing process.");
        List<Site> sitesList = sites.getSites();
        List<SiteEntity> entities = updateOrCreateSiteEntities(sitesList);

        entities.stream()
                .filter(siteEntity -> !siteEntity.getStatus().equals(Status.INDEXED))
                .forEach(this::indexSite);

        return new indexStatus(true, null);
    }

    private List<SiteEntity> updateOrCreateSiteEntities(List<Site> sitesList) {
        List<SiteEntity> entities = siteRepo.findAll();
        sitesList.forEach(site -> entities.add(siteRepo.findBySiteUrl(site.getUrl())
                .orElseGet(() -> createNewSiteEntity(site))));
        return entities;
    }

    private SiteEntity createNewSiteEntity(Site site) {
        SiteEntity entity = new SiteEntity(site.getUrl(), site.getName());
        siteRepo.save(entity);
        return entity;
    }

    private void indexSite(SiteEntity site) {
        site.setStatus_time(LocalDateTime.now());
        site.setStatus(Status.INDEXING);
        siteRepo.save(site);
        clearExistingData(site);
        ArrayList<String> listOfUrls = new ArrayList<>(List.of(site.getUrl()));
        ScrapTask scrapTask = new ScrapTask(siteRepo, pageRepo, lemmaRepo, indexRepo, site, listOfUrls, new TreeSet<>(), site.getUrl());
        forkJoinPool.execute(scrapTask);
    }

    private void clearExistingData(SiteEntity site) {
        List<Index> indices = indexRepo.findIndex4LemmaNPage(site.getUrl());
        indexRepo.deleteAll(indices);
        pageRepo.deleteBySite(site);
    }

    @Override
    public indexStatus stopIndexing() {
        if (!forkJoinPool.isShutdown()) {
            forkJoinPool.shutdownNow();
            return new indexStatus(true, null);
        }
        return new indexStatus(false, "Indexing has already been stopped.");
    }

    @Override
    public boolean addUpdatePage(String page) {
        try {
            URL url = new URL(URLDecoder.decode(page));
            url.toURI(); // Validate URL and URI
            return handlePageUpdate(url);
        } catch (MalformedURLException | URISyntaxException e) {
            log.error("Invalid URL: {}", page, e);
            return new pageStatus(false, e.getLocalizedMessage());
        }
    }

    private pageStatus handlePageUpdate(URL url) {
        SiteEntity site = siteRepo.findBySiteUrl(url.toString()).orElse(createNewSiteEntity(new Site(url.toString(), url.getHost())));
        site.setStatus_time(LocalDateTime.now());
        site.setStatus(Status.INDEXING);
        siteRepo.save(site);
        return new pageStatus(true, "Page added or updated successfully.");
    }
*/

    /*@Override
    protected void compute() {
        if (pageRepo.contains(url)) {
            return;
        }

        .add(url);

        writeLine(indentation + url);

        List<WebsiteCrawler> websiteCrawlers = new ArrayList<>();
        for (String childUrl : getChildUrls(url)) {
            try {
                Thread.sleep((long) (Math.random() * ((185-100) + 1) + 100));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            final WebsiteCrawler websiteCrawler = new WebsiteCrawler(childUrl);
            websiteCrawler.fork();
            websiteCrawlers.add(websiteCrawler);
        }

        for (WebsiteCrawler wc : websiteCrawlers) {
            try {
                wc.join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private Set<String> getChildUrls(final String url) {
        final Set<String> childUrls = new HashSet<>();

        try {
            final Set<String> urls = new HtmlParser(url).getUrls();

            for (final String childUrl : urls) {
                if (isValidUrl(childUrl) && isSameDomain(childUrl)) {
                    childUrls.add(childUrl);
                }
            }
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }

        return childUrls;
    }

    private boolean isValidUrl(final String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    private boolean isSameDomain(final String childUrl) throws URISyntaxException {
        final URI baseURI = new URI(url);
        final URI childURI = new URI(childUrl);

        return baseURI.getHost().equals(childURI.getHost());
    }

    private String getIndentation(final int depth) {
        return "\t".repeat(Math.max(0, depth));
    }

    private void writeLine(final String line) {
        try {
            Path filePath = Path.of(path);
            Files.writeString(filePath, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/
}
