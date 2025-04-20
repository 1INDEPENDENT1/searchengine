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
    List<IndexesEntity> findByPageEntity(@Param("page") PageEntity page);
    List<IndexesEntity> findByLemmaEntity(LemmaEntity lemma);
}
