package searchengine.repos;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.models.SiteEntity;

@Repository
public interface DbCleanerRepository extends JpaRepository<SiteEntity, Integer> {

    @Modifying
    @Transactional
    @Query(value = "TRUNCATE TABLE sites, page, lemmas, search_index RESTART IDENTITY CASCADE", nativeQuery = true)
    void truncateAll();
}