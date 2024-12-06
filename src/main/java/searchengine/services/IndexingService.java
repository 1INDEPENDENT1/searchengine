package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;

public interface IndexingService {
    boolean startIndexing();

    boolean stopIndexing();
}
