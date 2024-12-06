package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.stream.Collectors;

public class HtmlParser {
    private final String url;

    public HtmlParser(String url) {
        this.url = url;
    }

    public Set<String> getUrls() throws IOException, URISyntaxException {
        Document doc = Jsoup.connect(url).get();
        Elements links = doc.select("a[href]");
        URI baseURI = new URI(url);

        return links.parallelStream()  // Используем параллельные потоки
                .map(link -> link.attr("abs:href"))
                .filter(this::isValidUrl)
                .filter(href -> isSameDomain(href, baseURI))
                .filter(href -> !containsPdfKeyword(href))
                .collect(Collectors.toSet());
    }


    private boolean containsPdfKeyword(String url) {
        return url.toLowerCase().contains("pdf");
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
}

