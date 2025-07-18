package searchengine.services.impl;

import java.io.IOException;
import java.util.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.log4j.Log4j2;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.WrongCharaterException;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import org.springframework.stereotype.Service;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Transactional;
import searchengine.enums.LangEnum;
import searchengine.models.IndexesEntity;
import searchengine.models.LemmaEntity;
import searchengine.models.PageEntity;
import searchengine.models.SiteEntity;
import searchengine.repos.IndexesRepository;
import searchengine.repos.LemmaRepository;


@Log4j2
@Service
public class SiteIndexingImpl {
    LuceneMorphology morphRU = new RussianLuceneMorphology();
    LuceneMorphology morphEN = new EnglishLuceneMorphology();
    private final LemmaRepository lemmaRepository;
    private final IndexesRepository indexesRepository;
    public static List<String> wrongWords = new ArrayList<>();

    @PersistenceContext
    private EntityManager entityManager;

    public SiteIndexingImpl(LemmaRepository lemmaRepository, IndexesRepository indexesRepository) throws IOException {
        this.lemmaRepository = lemmaRepository;
        this.indexesRepository = indexesRepository;
    }

    @Transactional
    public Map<LemmaEntity, Integer> saveTextToLemmasAndIndexes(final String pageText, SiteEntity siteEntity, PageEntity pageEntity) {
        Map<LemmaEntity, Integer> lemmasAndCountWithKey =
                getLemmasAndCountWithKey(sortWordsOnRussianAndEnglishWords(pageText), siteEntity);

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

    private boolean isNotServiceWord(String word) {
        List<String> skipTags = List.of("МЕЖД", "ПРЕДЛ", "СОЮЗ", "ЧАСТ", "PREP", "CONJ", "ART", "PART");

        return skipTags.stream().noneMatch(word::contains);
    }

    private List<String> removeFunctionsWords(final List<String> words, final LangEnum lang) {
        List<String> primaryWords = words.stream().map(word -> getMorphInfoSave(word, lang))
                .filter(word -> !word.isBlank())
                .filter(this::isNotServiceWord).toList();

        return primaryWords.stream().map(this::getSimpleForm).toList();
    }

    private String getSimpleForm(final String word) {
        return word.substring(0, word.indexOf('|')).trim();
    }

    public HashMap<String, Integer> sortWordsOnRussianAndEnglishWords(final String pageText) {
        // Разделяем текст на слова, удаляем пунктуацию, пробелы, переводим в нижний регистр
        List<String> words = Arrays.asList(removeHtmlTags(pageText).split("[ \\t]+")).parallelStream()
                .map(s -> s.replaceAll("\\p{Punct}", "")) // Удаление пунктуации
                .map(String::trim)                       // Удаление лишних пробелов
                .map(String::toLowerCase)                // Перевод в нижний регистр
                .filter(s -> !s.isEmpty())         // Удаление пустых строк
                .toList();// Сбор в общий список

        HashMap<String, Integer> map = new HashMap<>();

        // Разделение на английские и русские слова
        List<String> englishWords = removeFunctionsWords(
                words.stream()
                        .filter(s -> s.matches("[a-z]+")) // Слова только из латинских букв
                        .toList(), LangEnum.ENG);

        englishWords.forEach(word -> map.compute(word, (k, v) -> v == null ? 1 : v + 1));

        List<String> russianWords = removeFunctionsWords(
                words.stream()
                        .filter(s -> s.matches("[а-яё]+")) // Слова только из кириллических букв
                        .toList(), LangEnum.RUS);

        russianWords.forEach(word -> map.compute(word, (k, v) -> v == null ? 1 : v + 1));

        log.debug("Wrong words {}", wrongWords);

        return map;
    }

    private static String removeHtmlTags(String htmlCode) {
        return htmlCode != null ? htmlCode.replaceAll("<[^>]*>", "").trim() : null;
    }

    public String getZeroForm(final String word) {
        String cleaned = word.replaceAll("[^\\p{IsAlphabetic}]", "").toLowerCase();
        if (cleaned.matches("[а-яё]+")) {
            return getSimpleForm(getMorphInfoSave(cleaned, LangEnum.RUS));
        } else if (cleaned.matches("[a-z]+")) {
            return getSimpleForm(getMorphInfoSave(cleaned, LangEnum.ENG));
        }
        return cleaned;
    }

    private String getMorphInfoSave(final String word, final LangEnum lang) {
        String basicWord;
        try {
            basicWord = lang == LangEnum.RUS ? morphRU.getMorphInfo(word).get(0) : morphEN.getMorphInfo(word).get(0);
        } catch (WrongCharaterException e) {
            log.error(e.getMessage());
            wrongWords.add(word);
            return "";
        }
        return basicWord;
    }

    //Todo: вынести в отдельный метод
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
