package searchengine.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.models.SiteEntity;
import searchengine.models.SiteStatusType;

import java.util.List;
import java.util.Optional;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {
    Optional<SiteEntity> findByUrl(final String url);
    boolean existsByStatus(SiteStatusType status);
}