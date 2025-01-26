package searchengine.tasks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.extern.log4j.Log4j2;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.WrongCharaterException;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import searchengine.enums.LangEnum;

@Log4j2
public class LemmaTask {
    LuceneMorphology morphRU = new RussianLuceneMorphology();
    LuceneMorphology morphEN = new EnglishLuceneMorphology();
    public static List<String> wrongWords = new ArrayList<>();

    public LemmaTask() throws IOException {
    }


    public boolean isNotServiceWord(String word) {
        List<String> skipTags = List.of("МЕЖД", "ПРЕДЛ", "СОЮЗ", "ЧАСТ", "PREP", "CONJ", "ART", "PART");

        boolean isNotServiceWord = skipTags.stream().noneMatch(word::contains);

        return isNotServiceWord;
    }

    public List<String> removeFunctionsWords(final List<String> words, final LangEnum lang) {
        List<String> primaryWords = words.stream().map(word -> getMorphInfoSave(word, lang))
                .filter(word -> !word.isBlank())
                .filter(this::isNotServiceWord).toList();

        return primaryWords.stream().map(this::getSimpleForm).toList();
    }

    private String getSimpleForm(final String word) {
        return word.substring(0, word.indexOf('|')).trim();
    }

    public void sortWordsOnRussianAndEnglishWords(final String pageText) {
        // Разделяем текст на слова, удаляем пунктуацию, пробелы, переводим в нижний регистр
        List<String> words = Arrays.asList(pageText.split("\\p{Blank}+")).parallelStream()
                .map(s -> s.replaceAll("\\p{Punct}", "")) // Удаление пунктуации
                .map(String::trim)                       // Удаление лишних пробелов
                .map(String::toLowerCase)                // Перевод в нижний регистр
                .filter(s -> !s.isEmpty())         // Удаление пустых строк
                .toList();// Сбор в общий список

        // Разделение на английские и русские слова
        List<String> englishWords = removeFunctionsWords(
                words.stream()
                        .filter(s -> s.matches("[a-z]+")) // Слова только из латинских букв
                        .toList(), LangEnum.ENG);

        List<String> russianWords = removeFunctionsWords(
                words.stream()
                        .filter(s -> s.matches("[а-яё]+")) // Слова только из кириллических букв
                        .toList(), LangEnum.RUS);

        // Вывод результата
        System.out.println("English words: " + englishWords);
        System.out.println("Russian words: " + russianWords);
    }

    public String getMorphInfoSave(final String word, final LangEnum lang) {
        String basicWord;
        try {
            basicWord = lang == LangEnum.RUS ? morphRU.getMorphInfo(word).get(0) : morphEN.getMorphInfo(word).get(0);
        } catch (WrongCharaterException e) {
            System.out.println(e.getMessage());
            wrongWords.add(word);
            return "";
        }
        // Регулярное выражение для удаления всего, что заключено в угловые скобки
        return basicWord;
    }
}
