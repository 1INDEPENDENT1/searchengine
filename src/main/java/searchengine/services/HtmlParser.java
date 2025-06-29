package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import org.jsoup.select.Elements;
import searchengine.models.SiteEntity;

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
    private final SiteEntity siteEntity;

    public HtmlParser(String url, SiteEntity siteEntity) {
        this.url = url;
        this.siteEntity = siteEntity;
    }

    public Set<String> getPaths() throws IOException, URISyntaxException {
        Document doc = null;
        final String homeUrl = siteEntity.getUrl();
        try {
            if (!url.equals(homeUrl)) {
                Jsoup.connect(homeUrl + url);
            } else {
                doc = Jsoup.connect(url).get();
            }
            if (doc != null) {
                Elements links = doc.select("a[href]");

                return links.parallelStream()  // Используем параллельные потоки
                        .map(link -> link.attr("abs:href"))
                        .filter(this::isValidUrl)
                        .filter(this::isHtmlPage)
                        .filter(href -> isSameDomain(href, homeUrl))
                        .map(this::getAbsolutePath)
                        .collect(Collectors.toSet());
            }
        } catch (SocketTimeoutException ignored) {
        }
        return new HashSet<>();
    }

    private boolean isValidUrl(final String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    private boolean isSameDomain(final String childUrl, String baseURIStr) {
        URI childURI;
        URI baseURI;
        try {
            childURI = new URI(childUrl);
            baseURI = new URI(baseURIStr);
        } catch (URISyntaxException e) {
            return false;
        }
        return childURI.getHost().contains(baseURI.getHost());
    }

    private String getAbsolutePath(final String url) {
        try {
            String[] path = new URI(url).getPath().split("\\?");
            return path[0];
        } catch (URISyntaxException e) {
            return "/";
        }
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

