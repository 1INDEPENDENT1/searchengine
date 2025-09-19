package searchengine.services.impl.indexing;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Log4j2
public class DbCleaner {
    @PersistenceContext
    private EntityManager em;

    @Transactional
    public void truncateAll() {
        em.createNativeQuery(
                "DELETE FROM sites; "
        ).executeUpdate();
    }
}
