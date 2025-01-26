package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.StatisticsService;
import searchengine.services.impl.IndexingServiceImpl;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingServiceImpl indexingService;

    public ApiController(StatisticsService statisticsService, IndexingServiceImpl indexingService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
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
    public boolean stopIndexing() {
        return indexingService.stopIndexing();
    }
}
