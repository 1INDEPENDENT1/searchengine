package searchengine.services.impl.searchImpl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.index.SearchResultDto;
import searchengine.dto.statistics.SearchResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchService {
    private final CachedSearchService cachedSearchService;

    public SearchResponse search(String query, String siteUrl, int offset, int limit) {
        List<SearchResultDto> allResults = cachedSearchService.getAllResults(query, siteUrl);
        int total = allResults.size();
        int toIndex = Math.min(offset + limit, total);

        if (offset >= total) {
            return new SearchResponse(true, total, List.of());
        }

        List<SearchResultDto> paged = allResults.subList(offset, toIndex);
        return new SearchResponse(true, total, paged);
    }
}
