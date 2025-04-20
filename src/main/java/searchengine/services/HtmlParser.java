package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HtmlParser {
    private static final List<String> IMAGE_EXTENSIONS = List.of(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".svg", ".webp", ".tiff"
    );

    private static final List<String> VIDEO_EXTENSIONS = List.of(
            ".mp4", ".avi", ".mkv", ".mov", ".webm"
    );

    private static final List<String> AUDIO_EXTENSIONS = List.of(
            ".mp3", ".wav", ".ogg"
    );

    private static final List<String> DOCUMENT_EXTENSIONS = List.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt"
    );

    private static final List<String> ARCHIVE_EXTENSIONS = List.of(
            ".zip", ".rar", ".7z", ".tar", ".gz"
    );

    private static final List<String> EXECUTABLE_EXTENSIONS = List.of(
            ".exe", ".bin", ".msi", ".sh"
    );

    private static final List<String> SCRIPT_AND_STYLE_EXTENSIONS = List.of(
            ".js", ".css"
    );

    private static final List<String> UNWANTED_KEYWORDS = List.of(
            "javascript:void", "#"
    );
    private final String url;

    public HtmlParser(String url) {
        this.url = url;
    }

    public Set<String> getUrls() throws IOException, URISyntaxException {
        try {
            Document doc = Jsoup.connect(url).get();
            Elements links = doc.select("a[href]");
            URI baseURI = new URI(url);

            return links.parallelStream()  // Используем параллельные потоки
                    .map(link -> link.attr("abs:href"))
                    .filter(this::isValidUrl)
                    .filter(this::isHtmlPage)
                    .filter(href -> isSameDomain(href, baseURI))
                    .collect(Collectors.toSet());
        }catch (SocketTimeoutException e) {
            return new HashSet<>();
        }
    }

    private boolean isValidUrl(final String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    private boolean isSameDomain(final String childUrl, URI baseURI)  {
        URI childURI;
        try {
            childURI = new URI(childUrl);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return childURI.getHost().contains(baseURI.getHost());
    }

    private boolean isHtmlPage(String url) {
        return !hasUnwantedExtension(url) && !containsUnwantedKeywords(url);
    }

    private boolean hasUnwantedExtension(String url) {
        String lowerCaseUrl = url.toLowerCase();
        return Stream.of(
                IMAGE_EXTENSIONS,
                VIDEO_EXTENSIONS,
                AUDIO_EXTENSIONS,
                DOCUMENT_EXTENSIONS,
                ARCHIVE_EXTENSIONS,
                EXECUTABLE_EXTENSIONS,
                SCRIPT_AND_STYLE_EXTENSIONS
        ).flatMap(List::stream).anyMatch(lowerCaseUrl::endsWith);
    }

    private boolean containsUnwantedKeywords(String url) {
        String lowerCaseUrl = url.toLowerCase();
        return UNWANTED_KEYWORDS.stream().anyMatch(lowerCaseUrl::contains);
    }
}

