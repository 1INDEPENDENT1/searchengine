package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class WebScraperService {
    public Document fetchDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("HeliontSearchBot/1.0 (Windows; U; Windows NT 10.0; en-US; rv:1.9.1.5)")
                .referrer("http://www.google.com")
                .get();
    }
}

