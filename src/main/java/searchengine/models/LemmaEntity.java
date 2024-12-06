package searchengine.models;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Getter
@Setter
@Table(name = "LEMMAS")
public class LemmaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @JoinColumn(name = "site_id")
    @ManyToOne(cascade = CascadeType.MERGE)
    private SiteEntity siteEntity;
    @Column(nullable = false)
    private String lemma;
    @Column(nullable = false)
    private int frequency;
}