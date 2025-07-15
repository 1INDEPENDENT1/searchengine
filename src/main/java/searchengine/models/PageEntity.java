package searchengine.models;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.*;

@Entity
@Getter
@Setter
@Table(name = "page", indexes = {@Index(name = "path_idx", columnList = "path")},
        uniqueConstraints = {@UniqueConstraint(columnNames = {"site_entity_id", "path"})}
)
@NoArgsConstructor
public class PageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @JoinColumn(name = "site_entity_id", nullable = false)
    @ManyToOne(cascade = CascadeType.MERGE)
    private SiteEntity siteEntity;
    @Column(nullable = false)
    private String path;
    @Column(nullable = false)
    private int code;
    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;

    public PageEntity(SiteEntity siteEntity, String path) {
        this.siteEntity = siteEntity;
        this.path = path;
    }
}