package searchengine.services.impl.indexing;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class DbCleaner {
    @PersistenceContext
    private final EntityManager em;

    @Transactional
    public void truncateAll() {
        // Для MySQL: нужно временно отключить FK проверки
        em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();

        em.createNativeQuery("TRUNCATE TABLE search_index").executeUpdate();
        em.createNativeQuery("TRUNCATE TABLE lemmas").executeUpdate();
        em.createNativeQuery("TRUNCATE TABLE page").executeUpdate();
        em.createNativeQuery("TRUNCATE TABLE sites").executeUpdate();

        // (опционально) сброс auto_increment
        em.createNativeQuery("ALTER TABLE search_index AUTO_INCREMENT = 1").executeUpdate();
        em.createNativeQuery("ALTER TABLE lemmas AUTO_INCREMENT = 1").executeUpdate();
        em.createNativeQuery("ALTER TABLE page AUTO_INCREMENT = 1").executeUpdate();
        em.createNativeQuery("ALTER TABLE sites AUTO_INCREMENT = 1").executeUpdate();

        em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
    }
}
