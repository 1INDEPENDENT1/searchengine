package searchengine.repos;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
