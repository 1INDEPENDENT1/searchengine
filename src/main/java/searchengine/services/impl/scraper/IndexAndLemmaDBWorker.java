package searchengine.services.impl.scraper;

import java.util.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.models.IndexesEntity;
import searchengine.models.LemmaEntity;
import searchengine.models.PageEntity;
import searchengine.models.SiteEntity;
import searchengine.repos.IndexesRepository;
import searchengine.repos.LemmaRepository;
import searchengine.services.impl.textWorkers.TextLemmaParser;


@Log4j2
@Service
@RequiredArgsConstructor
public class IndexAndLemmaDBWorker {
    private final LemmaRepository lemmaRepository;
    private final IndexesRepository indexesRepository;
    private final TextLemmaParser textLemmaParser;

    @Transactional
    public void saveTextToLemmasAndIndexes(final String pageText, SiteEntity siteEntity, PageEntity pageEntity) {
        getLemmasAndCountWithKey(textLemmaParser.sortWordsOnRussianAndEnglishWords(pageText), siteEntity, pageEntity);
    }

    public void getLemmasAndCountWithKey(
            HashMap<String, Integer> lemmaTexts, SiteEntity siteEntity, PageEntity pageEntity) {
        savesLemmas(lemmaTexts, siteEntity);

        saveIndexes(lemmaTexts, siteEntity, pageEntity);
    }

    private void savesLemmas(HashMap<String, Integer> lemmaTexts, SiteEntity siteEntity) {
        List<String> sorted = new ArrayList<>(lemmaTexts.keySet());
        Collections.sort(sorted);
        lemmaRepository.batchUpsertIncrement(siteEntity.getId(), sorted.toArray(new String[0]));
    }

    private void saveIndexes(HashMap<String, Integer> lemmaTexts, SiteEntity siteEntity, PageEntity pageEntity) {
        List<LemmaEntity> reloaded = lemmaRepository.findByLemmaIn(
                new ArrayList<>(lemmaTexts.keySet()), siteEntity.getId()
        );

        int n = reloaded.size();
        int[] pageIds  = new int[n];
        int[] lemmaIds = new int[n];
        float[] ranks  = new float[n];

        for (int i = 0; i < n; i++) {
            LemmaEntity le = reloaded.get(i);
            Integer cnt = lemmaTexts.get(le.getLemma());
            if (cnt == null) continue;

            pageIds[i]  = pageEntity.getId();
            lemmaIds[i] = le.getId();
            ranks[i]    = cnt;
        }

        indexesRepository.batchUpsertIndexes(pageIds, lemmaIds, ranks);
    }

    @Transactional
    public void removePageData(PageEntity page) {
        List<IndexesEntity> indexes = indexesRepository.findByPageEntity(page);
        for (IndexesEntity index : indexes) {
            LemmaEntity lemma = index.getLemmaEntity();
            lemma.setFrequency(lemma.getFrequency() - 1);
            if (lemma.getFrequency() <= 0) {
                lemmaRepository.delete(lemma);
            } else {
                lemmaRepository.save(lemma);
            }
        }
        indexesRepository.deleteAll(indexes);
    }
}
