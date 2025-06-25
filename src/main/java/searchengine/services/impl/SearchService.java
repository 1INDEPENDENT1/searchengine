package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import searchengine.dto.index.SearchResultDto;
import searchengine.dto.statistics.SearchResponse;
import searchengine.models.*;
import searchengine.repos.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class SearchService {
    private final LemmaRepository lemmaRepository;
    private final IndexesRepository indexesRepository;
    private final SiteRepository siteRepository;
    private final SiteIndexingImpl siteIndexingImpl;

    public SearchResponse search(String query, String siteUrl, int offset, int limit) {
        List<String> lemmas = siteIndexingImpl
                .sortWordsOnRussianAndEnglishWords(query)
                .keySet().stream().toList();

        if (lemmas.isEmpty()) {
            return SearchResponse.error("По запросу не найдено значимых слов");
        }

        List<SiteEntity> sites = (siteUrl != null)
                ? List.of(Objects.requireNonNull(siteRepository.findByUrl(siteUrl).orElse(null)))
                : siteRepository.findAll();

        Map<PageEntity, Float> relevanceMap = new HashMap<>();
        for (SiteEntity site : sites) {
            List<LemmaEntity> lemmaEntities = lemmaRepository.findByLemmaIn(lemmas, site.getId());
            if (lemmaEntities.isEmpty()) continue;

            int maxPages = (int) (site.getSitePageEntities().size() * 0.85);
            List<LemmaEntity> filtered = lemmaEntities.stream()
                    .filter(l -> l.getFrequency() <= maxPages)
                    .sorted(Comparator.comparingInt(LemmaEntity::getFrequency))
                    .toList();

            Map<PageEntity, List<IndexesEntity>> pageToIndexesMap = new HashMap<>();
            List<IndexesEntity> allIndexes = indexesRepository.findByLemmaEntityIn(filtered);

            Map<LemmaEntity, Map<PageEntity, List<IndexesEntity>>> lemmaToPageIndexes = new HashMap<>();
            for (IndexesEntity index : allIndexes) {
                LemmaEntity lemma = index.getLemmaEntity();
                PageEntity page = index.getPageEntity();

                lemmaToPageIndexes
                        .computeIfAbsent(lemma, l -> new HashMap<>())
                        .computeIfAbsent(page, p -> new ArrayList<>())
                        .add(index);
            }

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

            if (pageToIndexesMap.isEmpty()) continue;

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
        }

        List<SearchResultDto> results = relevanceMap.entrySet().stream()
                .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
                .skip(offset)
                .limit(limit)
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

        return new SearchResponse(true, relevanceMap.size(), results);
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
        int contextSize = 20; // сколько слов слева и справа от совпадения

        OptionalInt matchIndexOpt = IntStream.range(0, words.length)
                .filter(i -> lemmas.stream()
                        .anyMatch(lemma -> siteIndexingImpl.getZeroForm(words[i]).toLowerCase().contains(lemma.toLowerCase())))
                .findFirst();

        if (matchIndexOpt.isEmpty()) {
            return ""; // ничего не нашли
        }

        int matchIndex = matchIndexOpt.getAsInt();

        int start = Math.max(0, matchIndex - contextSize);
        int end = Math.min(words.length, matchIndex + contextSize + 1);

        StringBuilder snippet = new StringBuilder();
        for (int i = start; i < end; i++) {
            String word = words[i];
            if (i == matchIndex) {
                String cleanWord = words[i].replaceAll("\\p{Punct}", "");
                word = word.replaceAll("(?i)(" + cleanWord + ")", "<b>$1</b>");
            }

            snippet.append(word).append(" ");
        }

        return snippet.toString().trim() + "...";
    }
}
