package searchengine.controllers;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.StatisticsService;
import searchengine.services.impl.indexing.IndexingServiceImpl;
import searchengine.services.impl.searchImpl.SearchService;
import searchengine.web.errors.BadRequestException;
import searchengine.web.errors.ConflictException;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {
    private final StatisticsService statisticsService;
    private final IndexingServiceImpl indexingService;
    private final SearchService searchService;

    @GetMapping("/statistics")
    public StatisticsResponse statistics() {
        return statisticsService.getStatistics();
    }

    @GetMapping("/startIndexing")
    public Map<String, Object> startIndexing() {
        if (!indexingService.startIndexing()) {
            throw new ConflictException("Индексация уже запущена");
        }
        return Map.of("result", true);
    }

    @GetMapping("/stopIndexing")
    public Map<String, Object> stopIndexing() {
        boolean stopped = indexingService.stopIndexing();
        if (!stopped) {
            throw new BadRequestException("Индексация не запущена");
        }
        return Map.of("result", true);
    }

    @PostMapping("/indexPage")
    public Map<String, Object> indexPage(@RequestParam("url") String url) {
        Map<String, Object> result = indexingService.handlePageUpdate(url);
        if (Boolean.FALSE.equals(result.get("result"))) {
            String err = String.valueOf(result.getOrDefault("error", "Некорректный запрос"));
            throw new BadRequestException(err);
        }
        return result;
    }

    @GetMapping("/search")
    public Object search(@RequestParam String query,
                         @RequestParam(required = false) String site,
                         @RequestParam(defaultValue = "0") int offset,
                         @RequestParam(defaultValue = "20") int limit) {
        if (query == null || query.trim().isEmpty()) {
            throw new BadRequestException("Задан пустой поисковый запрос");
        }
        return searchService.search(query, site, offset, limit);
    }
}