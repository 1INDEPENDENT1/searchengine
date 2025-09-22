package searchengine.services.impl.textWorkers;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.lucene.morphology.WrongCharaterException;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Component;
import searchengine.enums.LangEnum;

import java.util.*;

@Log4j2
@Component
@RequiredArgsConstructor
public class TextLemmaParser {
    private final RussianLuceneMorphology morphRU;
    private final EnglishLuceneMorphology morphEN;
    public static List<String> wrongWords = Collections.synchronizedList(new ArrayList<>());

    public HashMap<String, Integer> sortWordsOnRussianAndEnglishWords(final String pageText) {
        List<String> words = Arrays.asList(removeHtmlTags(pageText).split("[ \\t]+")).parallelStream()
                .map(s -> s.replaceAll("\\p{Punct}", ""))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .toList();

        HashMap<String, Integer> map = new HashMap<>();

        List<String> englishWords = removeFunctionsWords(
                words.stream().filter(s -> s.matches("[a-z]+")).toList(), LangEnum.ENG);
        englishWords.forEach(word -> map.compute(word, (k, v) -> v == null ? 1 : v + 1));

        List<String> russianWords = removeFunctionsWords(
                words.stream().filter(s -> s.matches("[а-яё]+")).toList(), LangEnum.RUS);
        russianWords.forEach(word -> map.compute(word, (k, v) -> v == null ? 1 : v + 1));

        log.debug("Wrong words {}", wrongWords);

        return map;
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

    private String getSimpleForm(final String word) {
        return word.substring(0, word.indexOf('|')).trim();
    }

    private List<String> removeFunctionsWords(final List<String> words, final LangEnum lang) {
        return words.stream()
                .map(word -> getMorphInfoSave(word, lang))
                .filter(word -> !word.isBlank())
                .filter(this::isNotServiceWord)
                .map(this::getSimpleForm)
                .toList();
    }

    private boolean isNotServiceWord(String word) {
        List<String> skipTags = List.of("МЕЖД", "ПРЕДЛ", "СОЮЗ", "ЧАСТ", "PREP", "CONJ", "ART", "PART");
        return skipTags.stream().noneMatch(word::contains);
    }

    private String getMorphInfoSave(final String word, final LangEnum lang) {
        try {
            return lang == LangEnum.RUS ? morphRU.getMorphInfo(word).get(0) : morphEN.getMorphInfo(word).get(0);
        } catch (WrongCharaterException e) {
            log.error(e.getMessage());
            wrongWords.add(word);
            return "";
        }
    }

    private static String removeHtmlTags(String htmlCode) {
        return htmlCode != null ? htmlCode.replaceAll("<[^>]*>", "").trim() : null;
    }
}
