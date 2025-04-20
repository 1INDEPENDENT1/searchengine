package searchengine.dto.index;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchResultDto {
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private float relevance;
}
