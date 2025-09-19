package searchengine.repos;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.models.LemmaEntity;
import searchengine.models.SiteEntity;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {
    long countBySiteEntity(SiteEntity siteEntity);

    @Query("SELECT l FROM LemmaEntity l WHERE l.lemma IN :lemmas AND l.siteEntity.id = :siteId")
    List<LemmaEntity> findByLemmaIn(@Param("lemmas") List<String> lemmas, @Param("siteId") int siteId);

    @Query("SELECT l FROM LemmaEntity l WHERE l.lemma = :lemma AND l.siteEntity.id = :siteId")
    List<LemmaEntity> findByLemmaIn(@Param("lemmas") String lemma, @Param("siteId") int siteId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(
            value =
                    "INSERT INTO lemmas(site_id, lemma, frequency) " +
                            "VALUES (:siteId, :lemma, 1) " +
                            "ON CONFLICT (site_id, lemma) " +
                            "DO UPDATE SET frequency = lemmas.frequency + EXCLUDED.frequency",
            nativeQuery = true
    )
    void upsertAndIncrement(@Param("siteId") int siteId, @Param("lemma") String lemma);
}
