package searchengine.repos;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;
import searchengine.models.LemmaEntity;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {
}
