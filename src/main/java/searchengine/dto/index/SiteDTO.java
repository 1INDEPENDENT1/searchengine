package searchengine.dto.index;

import java.time.LocalDateTime;


public class SiteDTO {
    private int id;
    private SiteStatusType status;
    private LocalDateTime statusTime;
    private String lastError;
    private String url;
    private String name;
}
