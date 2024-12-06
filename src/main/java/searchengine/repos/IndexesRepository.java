package searchengine.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.models.IndexesEntity;
import searchengine.models.LemmaEntity;
import searchengine.models.PageEntity;

import java.util.List;

@Repository
public interface IndexesRepository extends JpaRepository<IndexesEntity, Integer> {
    @Query("SELECT t FROM IndexesEntity t WHERE t.lemmaEntity = :lemma and t.pageEntity = :page")
    IndexesEntity findIndex4LemmaNPage(@Param("lemma") LemmaEntity lemmaName, @Param("page") PageEntity page);

    @Query("SELECT t FROM IndexesEntity t WHERE t.lemmaEntity = :lemma")
    List<IndexesEntity> findIndex4Lemma(@Param("lemma") LemmaEntity lemma);

    @Query("SELECT t FROM IndexesEntity t WHERE t.lemmaEntity in (:lemmas) and t.pageEntity in (:pages) order by t.rank desc")
    List<IndexesEntity> findIndex4Lemmas(@Param("lemmas") List<LemmaEntity> lemmas, @Param("pages") List<PageEntity> pages);

    @Query("SELECT t FROM IndexesEntity t WHERE t.pageEntity = :page")
    List<IndexesEntity> findIndex4LemmaNPage(@Param("page") PageEntity page);
}
