package searchengine.models;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Getter
@Setter
@Table(name = "SEARCH_INDEX", indexes = {@Index(name = "idx_page_lemma", columnList = "page_id, lemma_id", unique = true)})
public class IndexesEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @JoinColumn(name = "page_id", nullable = false)
    @ManyToOne(cascade = CascadeType.MERGE)
    private PageEntity pageEntity;

    @JoinColumn(name = "lemma_id", nullable = false)
    @ManyToOne(cascade = CascadeType.MERGE)
    private LemmaEntity lemmaEntity;

    @Column(nullable = false, name = "`rank`")
    private float rank;
}
