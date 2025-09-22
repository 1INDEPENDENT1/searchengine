package searchengine.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.models.IndexesEntity;
import searchengine.models.LemmaEntity;
import searchengine.models.PageEntity;

import java.util.List;

@Repository
public interface IndexesRepository extends JpaRepository<IndexesEntity, Integer> {
    List<IndexesEntity> findByPageEntity(@Param("page") PageEntity page);
    List<IndexesEntity> findByLemmaEntityIn(List<LemmaEntity> lemmas);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query(value = """
    INSERT INTO search_index(page_id, lemma_id, "rank")
    SELECT t.page_id, t.lemma_id, t.rank
    FROM unnest(CAST(:pageIds AS int[]),
                CAST(:lemmaIds AS int[]),
                CAST(:ranks    AS real[])) AS t(page_id, lemma_id, rank)
    ON CONFLICT (page_id, lemma_id)
    DO UPDATE SET "rank" = EXCLUDED."rank"
    """, nativeQuery = true)
    void batchUpsertIndexes(@Param("pageIds") int[] pageIds,
                            @Param("lemmaIds") int[] lemmaIds,
                            @Param("ranks") float[] ranks);
}
