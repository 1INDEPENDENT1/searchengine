package searchengine.services.impl.scraper;

import java.io.IOException;
import java.util.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.log4j.Log4j2;

import org.springframework.stereotype.Service;
import org.springframework.transaction.UnexpectedRollbackException;
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
public class IndexAndLemmaDBWorker {
    private final LemmaRepository lemmaRepository;
    private final IndexesRepository indexesRepository;
    private final TextLemmaParser textLemmaParser;
    @PersistenceContext
    private EntityManager entityManager;

    public IndexAndLemmaDBWorker(LemmaRepository lemmaRepository, IndexesRepository indexesRepository, TextLemmaParser textLemmaParser) throws IOException {
        this.lemmaRepository = lemmaRepository;
        this.indexesRepository = indexesRepository;
        this.textLemmaParser = textLemmaParser;
    }

    @Transactional
    public Map<LemmaEntity, Integer> saveTextToLemmasAndIndexes(final String pageText, SiteEntity siteEntity, PageEntity pageEntity) {
        Map<LemmaEntity, Integer> lemmasAndCountWithKey =
                getLemmasAndCountWithKey(textLemmaParser.sortWordsOnRussianAndEnglishWords(pageText), siteEntity);

        try {
            addOrUpdateLemmas(lemmasAndCountWithKey, siteEntity, pageEntity);
            return Collections.emptyMap();
        } catch (Exception e) {
            if (isDeadlockException(e)) {
                log.warn("Deadlock detected for page {} — caching for finalization later", pageEntity.getPath());
            }
            throw new UnexpectedRollbackException("UnexpectedRollbackException in page " + pageEntity.getPath());
        }
    }

    private boolean isDeadlockException(Exception e) {
        return e.getMessage() != null && e.getMessage().contains("Deadlock");
    }

    @Transactional
    public void addOrUpdateLemmas(Map<LemmaEntity, Integer> lemmaAndCountOnPage, SiteEntity siteEntity, PageEntity page) {
        batchInsertOrUpdate(new ArrayList<>(lemmaAndCountOnPage.keySet()));

        List<LemmaEntity> reloadedLemmas = lemmaRepository.findByLemmaIn(
                lemmaAndCountOnPage.keySet().stream().map(LemmaEntity::getLemma).toList(),
                siteEntity.getId()
        );

        Map<LemmaEntity, Integer> reloadedLemmaAndCount = new HashMap<>();
        for (LemmaEntity reloadedLemma : reloadedLemmas) {
            lemmaAndCountOnPage.entrySet().stream()
                    .filter(entry -> entry.getKey().getLemma().equals(reloadedLemma.getLemma()))
                    .findFirst()
                    .ifPresent(entry -> reloadedLemmaAndCount.put(reloadedLemma, entry.getValue()));
        }

        batchInsertOrUpdateIndexes(addIndexes(reloadedLemmaAndCount, page));
    }

    public Map<LemmaEntity, Integer> getLemmasAndCountWithKey(HashMap<String, Integer> lemmaTexts, SiteEntity siteEntity) {
        List<LemmaEntity> existingLemmas = lemmaRepository.findByLemmaIn(new ArrayList<>(lemmaTexts.keySet()), siteEntity.getId());
        Map<LemmaEntity, Integer> lemmasAndCount = new HashMap<>();
        for (LemmaEntity lemma : existingLemmas) {
            Integer count = lemmaTexts.get(lemma.getLemma());
            if (count != null) {
                lemma.setFrequency(lemma.getFrequency() + 1);
                lemmasAndCount.put(lemma, count);
                lemmaTexts.remove(lemma.getLemma());
            }
        }

        lemmaTexts.forEach((lemma, count) -> {
            LemmaEntity lemmaEntity = new LemmaEntity();
            lemmaEntity.setLemma(lemma);
            lemmaEntity.setFrequency(1);
            lemmaEntity.setSiteEntity(siteEntity);
            lemmasAndCount.put(lemmaEntity, count);
        });
        return lemmasAndCount;
    }

    public List<IndexesEntity> addIndexes(Map<LemmaEntity, Integer> lemmaTexts, PageEntity page) {
        List<IndexesEntity> newIndexes = new ArrayList<>();

        for (Map.Entry<LemmaEntity, Integer> lemma : lemmaTexts.entrySet()) {
            IndexesEntity index = new IndexesEntity();
            index.setLemmaEntity(lemma.getKey());
            index.setPageEntity(page);
            index.setRank(lemma.getValue());
            newIndexes.add(index);
        }

        return newIndexes;
    }

    public void batchInsertOrUpdate(List<LemmaEntity> lemmas) {
        if (lemmas.isEmpty()) {
            return;
        }

        lemmas = new ArrayList<>(lemmas);

        lemmas.sort(
                Comparator.comparing(LemmaEntity::getLemma)
                        .thenComparing(l -> l.getSiteEntity().getId())
        );

        StringBuilder queryBuilder = new StringBuilder("INSERT INTO lemmas (lemma, site_id, frequency) VALUES ");

        for (LemmaEntity lemma : lemmas) {
            queryBuilder.append("('")
                    .append(lemma.getLemma()).append("', ")
                    .append(lemma.getSiteEntity().getId()).append(", ")
                    .append(lemma.getFrequency()).append("), ");
        }

        // Убираем последнюю запятую
        String sql = queryBuilder.substring(0, queryBuilder.length() - 2) +
                " ON DUPLICATE KEY UPDATE frequency = frequency + 1";

        // Используем EntityManager, так как `@Query` не поддерживает динамический SQL
        entityManager.createNativeQuery(sql).executeUpdate();

    }

    public void batchInsertOrUpdateIndexes(List<IndexesEntity> indexes) {
        if (indexes.isEmpty()) {
            return;
        }

        StringBuilder queryBuilder = new StringBuilder(
                "INSERT INTO search_index (page_id, lemma_id, `rank`) VALUES ");

        for (IndexesEntity index : indexes) {
            queryBuilder.append("(")
                    .append(index.getPageEntity().getId()).append(", ")
                    .append(index.getLemmaEntity().getId()).append(", ")
                    .append(index.getRank()).append("), ");
        }

        // Убираем последнюю запятую и пробел
        queryBuilder.setLength(queryBuilder.length() - 2);

        // Добавляем ON DUPLICATE KEY UPDATE (обновим только rank)
        queryBuilder.append(" ON DUPLICATE KEY UPDATE `rank` = VALUES(`rank`)");

        entityManager.createNativeQuery(queryBuilder.toString()).executeUpdate();
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
