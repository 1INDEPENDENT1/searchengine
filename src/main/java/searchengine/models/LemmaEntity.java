package searchengine.models;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(
        name = "LEMMAS",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"lemma", "site_id"})
        }
)
public class LemmaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @ManyToOne(cascade = CascadeType.MERGE)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity siteEntity;
    @Column(nullable = false)
    private String lemma;
    @Column(nullable = false)
    private int frequency;
}