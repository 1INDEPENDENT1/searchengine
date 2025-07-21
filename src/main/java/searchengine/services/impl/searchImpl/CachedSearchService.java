package searchengine.services.impl.searchImpl;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import searchengine.dto.index.SearchResultDto;

import searchengine.models.*;
import searchengine.repos.*;
import searchengine.services.impl.scraper.SiteIndexingImpl;

import java.util.*;
import java.util.stream.IntStream;


@Service
@RequiredArgsConstructor
public class CachedSearchService {
    private final LemmaRepository lemmaRepository;
    private final IndexesRepository indexesRepository;
    private final SiteRepository siteRepository;
    private final SiteIndexingImpl siteIndexingImpl;

    @Cacheable(value = "searchResults", key = "#query + '|' + (#siteUrl != null ? #siteUrl : '')")
    public List<SearchResultDto> getAllResults(String query, String siteUrl) {
        List<String> lemmas = siteIndexingImpl
                .sortWordsOnRussianAndEnglishWords(query)
                .keySet().stream().toList();

        if (lemmas.isEmpty()) {
            return List.of();
        }

        List<SiteEntity> sites = (siteUrl != null)
                ? List.of(Objects.requireNonNull(siteRepository.findByUrl(siteUrl).orElse(null)))
                : siteRepository.findAll();

        Map<PageEntity, Float> relevanceMap = new HashMap<>();
        for (SiteEntity site : sites) {
            List<LemmaEntity> filtered = getLemmaEntitiesFromSite(site, lemmas);
            if (filtered == null) continue;

            List<IndexesEntity> allIndexes = indexesRepository.findByLemmaEntityIn(filtered);
            Map<LemmaEntity, Map<PageEntity, List<IndexesEntity>>> lemmaToPageIndexes = getLemmaEntityWithInfo(allIndexes);
            Map<PageEntity, List<IndexesEntity>> pageToIndexesMap = findCommonPagesForAllLemmas(filtered, lemmaToPageIndexes);
            if (pageToIndexesMap.isEmpty()) continue;
            relevanceMap.putAll(getAbsRelevant(pageToIndexesMap));
        }

        return createSearchResultDto(relevanceMap, lemmas);
    }

    private List<SearchResultDto> createSearchResultDto(Map<PageEntity, Float> relevanceMap, List<String> lemmas) {
        return relevanceMap.entrySet().stream()
                .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
                .map(entry -> {
                    PageEntity page = entry.getKey();
                    SiteEntity site = page.getSiteEntity();
                    String snippet = generateSnippet(page.getContent(), lemmas);
                    String title = extractTitle(page.getContent());
                    return new SearchResultDto(
                            site.getUrl(), site.getName(),
                            page.getPath(), title,
                            snippet, entry.getValue()
                    );
                })
                .toList();
    }

    private static Map<PageEntity, Float> getAbsRelevant(Map<PageEntity, List<IndexesEntity>> pageToIndexesMap) {
        Map<PageEntity, Float> relevanceMap = new HashMap<>();
        float maxAbsRel = 0f;
        Map<PageEntity, Float> absRelMap = new HashMap<>();
        for (Map.Entry<PageEntity, List<IndexesEntity>> entry : pageToIndexesMap.entrySet()) {
            float absRel = 0f;
            for (IndexesEntity index : entry.getValue()) {
                absRel += index.getRank();
            }
            absRelMap.put(entry.getKey(), absRel);
            if (absRel > maxAbsRel) maxAbsRel = absRel;
        }

        for (Map.Entry<PageEntity, Float> entry : absRelMap.entrySet()) {
            float rel = entry.getValue() / maxAbsRel;
            relevanceMap.put(entry.getKey(), rel);
        }
        return relevanceMap;
    }

    private static Map<PageEntity, List<IndexesEntity>> findCommonPagesForAllLemmas(
            List<LemmaEntity> filtered, Map<LemmaEntity, Map<PageEntity, List<IndexesEntity>>> lemmaToPageIndexes) {
        Map<PageEntity, List<IndexesEntity>> pageToIndexesMap = new HashMap<>();
        boolean isFirst = true;
        for (LemmaEntity lemma : filtered) {
            Map<PageEntity, List<IndexesEntity>> pageMap = lemmaToPageIndexes.get(lemma);
            if (pageMap == null) {
                pageToIndexesMap.clear();
                break;
            }
            if (isFirst) {
                pageToIndexesMap.putAll(pageMap);
                isFirst = false;
            } else {
                pageToIndexesMap.keySet().retainAll(pageMap.keySet());
                pageToIndexesMap.replaceAll((p, v) -> {
                    List<IndexesEntity> newList = new ArrayList<>(v);
                    newList.addAll(pageMap.get(p));
                    return newList;
                });
            }
            if (pageToIndexesMap.isEmpty()) {
                break;
            }
        }
        return pageToIndexesMap;
    }

    private static Map<LemmaEntity, Map<PageEntity, List<IndexesEntity>>> getLemmaEntityWithInfo(List<IndexesEntity> allIndexes) {
        Map<LemmaEntity, Map<PageEntity, List<IndexesEntity>>> lemmaToPageIndexes = new HashMap<>();
        for (IndexesEntity index : allIndexes) {
            LemmaEntity lemma = index.getLemmaEntity();
            PageEntity page = index.getPageEntity();

            lemmaToPageIndexes
                    .computeIfAbsent(lemma, l -> new HashMap<>())
                    .computeIfAbsent(page, p -> new ArrayList<>())
                    .add(index);
        }
        return lemmaToPageIndexes;
    }

    private List<LemmaEntity> getLemmaEntitiesFromSite(SiteEntity site, List<String> lemmas) {
        List<LemmaEntity> lemmaEntities = lemmaRepository.findByLemmaIn(lemmas, site.getId());
        if (lemmaEntities.isEmpty()) return null;

        int maxPages = (int) (site.getSitePageEntities().size() * 0.85);
        return lemmaEntities.stream()
                .filter(l -> l.getFrequency() <= maxPages)
                .sorted(Comparator.comparingInt(LemmaEntity::getFrequency))
                .toList();
    }

    private String extractTitle(String content) {
        try {
            Document doc = Jsoup.parse(content);
            Element titleEl = doc.selectFirst("title");
            return titleEl != null ? titleEl.text() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String generateSnippet(String content, List<String> lemmas) {
        String text = Jsoup.parse(content).text();

        String[] words = text.split("\\s+");
        int contextSize = 20;
        OptionalInt matchIndexOpt = IntStream.range(0, words.length)
                .filter(i -> lemmas.stream()
                        .anyMatch(lemma -> siteIndexingImpl.getZeroForm(words[i]).toLowerCase().contains(lemma.toLowerCase())))
                .findFirst();

        if (matchIndexOpt.isEmpty()) {
            return "";
        }

        int matchIndex = matchIndexOpt.getAsInt();

        int start = Math.max(0, matchIndex - contextSize);
        int end = Math.min(words.length, matchIndex + contextSize + 1);

        StringBuilder snippet = new StringBuilder();
        for (int i = start; i < end; i++) {
            String word = words[i];

            String finalWord = word;
            boolean shouldHighlight = lemmas.stream()
                    .anyMatch(lemma -> siteIndexingImpl.getZeroForm(finalWord).toLowerCase().contains(lemma.toLowerCase()));

            if (shouldHighlight) {
                String cleanWord = word.replaceAll("\\p{Punct}", "");
                word = word.replaceAll("(?i)(" + cleanWord + ")", "<b>$1</b>");
            }

            snippet.append(word).append(" ");
        }

        return snippet.toString().trim() + "...";
    }
}
