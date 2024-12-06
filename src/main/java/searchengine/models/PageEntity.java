package searchengine.models;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Index;
import javax.persistence.*;

@Entity
@Getter
@Setter
@Table(name = "page", indexes = {@Index(name = "path_idx", columnList = "path")})
@NoArgsConstructor
public class PageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @JoinColumn(nullable = false)
    @ManyToOne(cascade = CascadeType.MERGE)
    private SiteEntity siteEntity;
    @Column(unique = true, nullable = false)
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