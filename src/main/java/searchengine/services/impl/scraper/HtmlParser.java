package searchengine.services.impl.scraper;

import lombok.extern.log4j.Log4j2;
import org.jsoup.HttpStatusException;
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

@Log4j2
public class HtmlParser {
    private static final int MAX_PATH_LENGTH = 255; // Защита от длинных path

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
            "javascript:void"
    );

    private final String url;
    private final SiteEntity siteEntity;

    public HtmlParser(String url, SiteEntity siteEntity) {
        this.url = url;
        this.siteEntity = siteEntity;
    }

    public Set<String> getPaths() {
        final String homeUrl = siteEntity.getUrl();
        try {
            Document doc = Jsoup.connect(homeUrl + url).get();
            Elements links = doc.select("a[href]");

            return links.stream()
                    .map(link -> link.attr("abs:href"))
                    .filter(this::isValidUrl)
                    .filter(href -> isSameDomain(href, homeUrl))
                    .map(this::getCleanedUrl)
                    .filter(this::isHtmlPage)
                    .filter(this::isAcceptableLength)
                    .collect(Collectors.toSet());

        } catch (IOException e) {
            if (e instanceof HttpStatusException statusEx) {
                log.warn("Status error {} for: {}", statusEx.getStatusCode(), homeUrl + url);
            } else if (e instanceof SocketTimeoutException) {
                log.warn("Timeout while connecting to: {}", url);
            } else {
                log.warn("Other IO error: {}", e.getMessage());
            }
        }
        return new HashSet<>();
    }

    private boolean isValidUrl(final String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private boolean isSameDomain(final String childUrl, String baseURIStr) {
        try {
            URI childURI = new URI(childUrl);
            URI baseURI = new URI(baseURIStr);
            String childHost = childURI.getHost();
            String baseHost = baseURI.getHost();

            if (childHost == null || baseHost == null) {
                return false;
            }

            return childHost.equalsIgnoreCase(baseHost);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private String getCleanedUrl(String fullUrl) {
        try {
            URI siteUri = new URI(siteEntity.getUrl());
            String sitePath = siteUri.getPath();
            if (sitePath == null) sitePath = "";

            URI uri = new URI(fullUrl);
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return "/";
            }

            path = path.replaceAll("/{2,}", "/");
            if (!path.startsWith("/")) {
                path = "/" + path;
            }

            if (!sitePath.isBlank() && path.startsWith(sitePath)) {
                path = path.substring(sitePath.length());
                if (!path.startsWith("/")) {
                    path = "/" + path;
                }
            }

            String query = uri.getQuery();
            if (query != null && !query.isBlank()) {
                path += "?" + query;
            }

            return path;

        } catch (URISyntaxException e) {
            return "/";
        }
    }

    public boolean isHtmlPage(String url) {
        return !hasUnwantedExtension(url) && !containsUnwantedKeywords(url);
    }

    private static boolean hasUnwantedExtension(String url) {
        String lowerUrl = url.toLowerCase();
        return Stream.of(
                IMAGE_EXTENSIONS,
                VIDEO_EXTENSIONS,
                AUDIO_EXTENSIONS,
                DOCUMENT_EXTENSIONS,
                ARCHIVE_EXTENSIONS,
                EXECUTABLE_EXTENSIONS,
                SCRIPT_AND_STYLE_EXTENSIONS
        ).flatMap(List::stream).anyMatch(lowerUrl::endsWith);
    }


    private static boolean containsUnwantedKeywords(String url) {
        return UNWANTED_KEYWORDS.stream().anyMatch(url::contains);
    }

    private boolean isAcceptableLength(String path) {
        if (path.length() > MAX_PATH_LENGTH) {
            log.warn("Filtered out too long path: {}", path);
            return false;
        }
        return true;
    }
}
