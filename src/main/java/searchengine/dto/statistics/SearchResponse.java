package searchengine.dto.statistics;

import lombok.*;
import searchengine.dto.index.SearchResultDto;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchResponse {
    private boolean result;
    private int count;
    private List<SearchResultDto> data;
    private String error;

    public static SearchResponse error(String errorMessage) {
        SearchResponse response = new SearchResponse();
        response.setResult(false);
        response.setError(errorMessage);
        return response;
    }

    public SearchResponse(boolean result, int count, List<SearchResultDto> data) {
        this.result = result;
        this.count = count;
        this.data = data;
        this.error = null;
    }
}
