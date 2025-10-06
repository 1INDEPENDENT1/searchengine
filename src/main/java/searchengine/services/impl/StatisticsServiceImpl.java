package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.models.SiteEntity;
import searchengine.repos.LemmaRepository;
import searchengine.repos.PageRepository;
import searchengine.repos.SiteRepository;
import searchengine.services.StatisticsService;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SitesList sites;
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    private final LemmaRepository lemmaRepo;

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);
        int pages = 0;
        int lemmas = 0;
        String status = "FAILED";
        String error = "Not indexed yet";
        long statusTime = System.currentTimeMillis();

        List<DetailedStatisticsItem> detailed = new ArrayList<>();

        for (Site site : sites.getSites()) {
            SiteEntity siteEntity = siteRepo.findByUrl(site.getUrl()).orElse(null);

            if (siteEntity != null) {
                pages = Math.toIntExact(pageRepo.countBySiteEntity(siteEntity));
                lemmas = Math.toIntExact(lemmaRepo.countBySiteEntity(siteEntity));
                status = siteEntity.getStatus().toString();
                error = siteEntity.getLastError();
                statusTime = siteEntity.getStatusTime()
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli();
            }

            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            item.setPages(pages);
            item.setLemmas(lemmas);
            item.setStatus(status);
            item.setError(error);
            item.setStatusTime(statusTime);
            detailed.add(item);
            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + lemmas);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
}