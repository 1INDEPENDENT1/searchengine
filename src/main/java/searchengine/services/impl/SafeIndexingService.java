package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import searchengine.models.PageEntity;
import searchengine.models.SiteEntity;

@Service
@RequiredArgsConstructor
public class SafeIndexingService {

    private final SiteIndexingImpl siteIndexingImpl;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveTextWithNewTransaction(String pageText, SiteEntity site, PageEntity page) {
        siteIndexingImpl.saveTextToLemmasAndIndexes(pageText, site, page);
    }
}