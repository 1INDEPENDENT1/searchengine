package searchengine.services.impl.indexing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatesConfig {
    @Bean
    public IndexingGate indexingGate() {
        return IndexingGate.INSTANCE;
    }
}