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

            // Обрезаем по частоте (например, > 70% страниц)
            int maxPages = (int) (site.getSitePageEntities().size() * 0.7);
            List<LemmaEntity> filtered = lemmaEntities.stream()
                    .filter(l -> l.getFrequency() <= maxPages)
                    .sorted(Comparator.comparingInt(LemmaEntity::getFrequency))
                    .toList();

            Set<PageEntity> resultPages = null;
            for (LemmaEntity lemma : filtered) {
                List<IndexesEntity> indexes = indexesRepository.findByLemmaEntity(lemma);
                Set<PageEntity> pages = indexes.stream().map(IndexesEntity::getPageEntity).collect(Collectors.toSet());
                if (resultPages == null) {
                    resultPages = pages;
                } else {
                    resultPages.retainAll(pages);
                }
                if (resultPages.isEmpty()) break;
            }

            if (resultPages == null || resultPages.isEmpty()) continue;

            float maxAbsRel = 0f;
            Map<PageEntity, Float> absRelMap = new HashMap<>();
            for (PageEntity page : resultPages) {
                float absRel = indexesRepository.findByPageEntity(page).stream()
                        .filter(i -> filtered.contains(i.getLemmaEntity()))
                        .map(IndexesEntity::getRank)
                        .reduce(0f, Float::sum);
                absRelMap.put(page, absRel);
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
        text = text.length() > 500 ? text.substring(0, 500) : text;
        for (String lemma : lemmas) {
            text = text.replaceAll("(?i)(\\b" + lemma + "\\b)", "<b>$1</b>");
        }
        return text;
    }
}
