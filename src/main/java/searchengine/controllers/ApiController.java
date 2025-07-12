package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.StatisticsService;
import searchengine.services.impl.IndexingServiceImpl;
import searchengine.services.impl.SearchService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingServiceImpl indexingService;
    private final SearchService searchService;

    public ApiController(StatisticsService statisticsService, IndexingServiceImpl indexingService, SearchService searchService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.searchService = searchService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<Map<String, Object>> startIndexing() {
        final boolean status = indexingService.startIndexing();
        Map<String, Object> response = new HashMap<>();

        if (!status) {
            response.put("result", false);
            response.put("error", "Индексация уже запущена");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        } else {
            response.put("result", true);
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Map<String, Object>> stopIndexing() {
        Map<String, Object> response = indexingService.stopIndexing()
                ? Map.of("result", true)
                : Map.of("result", false, "error", "Индексация не запущена");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/indexPage")
    public ResponseEntity<?> indexPage(@RequestParam("url") String url) {
        Map<String, Object> result = indexingService.handlePageUpdate(url);

        if (Boolean.FALSE.equals(result.get("result"))) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String query,
                                    @RequestParam(required = false) String site,
                                    @RequestParam(defaultValue = "0") int offset,
                                    @RequestParam(defaultValue = "20") int limit) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("result", false, "error", "Задан пустой поисковый запрос"));
        }
        return ResponseEntity.ok(searchService.search(query, site, offset, limit));
    }


}
