package searchengine.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.models.PageEntity;
import searchengine.models.SiteEntity;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Integer> {
    PageEntity findByPath(String path);

    @Query("SELECT p FROM PageEntity p WHERE p.siteEntity = :siteEntity")
    List<PageEntity> findAllBySiteEntity(SiteEntity siteEntity);


    @Query("SELECT p FROM PageEntity p WHERE p.path = :path AND p.siteEntity = :siteEntity")
    PageEntity findByPathAndSiteEntity(String path, SiteEntity siteEntity);

    long countBySiteEntity(SiteEntity siteEntity);
}

