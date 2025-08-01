package searchengine.services.impl.searchImpl;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.services.impl.textWorkers.TextLemmaParser;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class SnippetGenerator {
    private final TextLemmaParser textLemmaParser;

    public String generateSnippet(String content, List<String> lemmas) {
        String text = Jsoup.parse(content).text();

        String[] words = text.split("\\s+");
        int contextSize = 20;
        OptionalInt matchIndexOpt = IntStream.range(0, words.length)
                .filter(i -> lemmas.stream()
                        .anyMatch(lemma -> textLemmaParser.getZeroForm(words[i]).toLowerCase().contains(lemma.toLowerCase())))
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
                    .anyMatch(lemma -> textLemmaParser.getZeroForm(finalWord).toLowerCase().contains(lemma.toLowerCase()));

            if (shouldHighlight) {
                String cleanWord = word.replaceAll("\\p{Punct}", "");
                word = word.replaceAll("(?i)(" + cleanWord + ")", "<b>$1</b>");
            }

            snippet.append(word).append(" ");
        }

        return snippet.toString().trim() + "...";
    }
}
