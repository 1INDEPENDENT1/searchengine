package searchengine.models;

import lombok.*;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "sites")
public class SiteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SiteStatusType status;
    @Column(nullable = false)
    private LocalDateTime statusTime;
    @Column(columnDefinition = "TEXT")
    private String lastError;
    @Column(nullable = false)
    private String url;
    @Column(nullable = false)
    private String name;
    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "siteEntity")
    private List<PageEntity> sitePageEntities = new ArrayList<>();

    public SiteEntity(String url, String name, SiteStatusType status){
        this.status = status;
        this.statusTime = LocalDateTime.now();
        this.lastError = null;
        this.url = url;
        this.name = name;
    }

    public SiteEntity(SiteStatusType status, LocalDateTime statusTime, String lastError, String url, String name){
        this.status = status;
        this.statusTime = statusTime;
        this.lastError = lastError;
        this.url = url;
        this.name = name;
    }
}