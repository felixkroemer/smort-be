package com.felixkroemer.smort.infrastructure.sqlite.anki;

import jakarta.persistence.*;
import java.util.List;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Getter
@Immutable
@Table(name = "decks")
public class AnkiDeckEntity {

  @Id
  @Column(columnDefinition = "integer")
  private Long id;

  @OneToMany(mappedBy = "deck")
  private List<AnkiCardEntity> cards;

  @Column(name = "name")
  private String name;

  @Column(name = "mtime_secs", columnDefinition = "integer")
  private Long mtimeSecs;

  @Column(name = "usn", columnDefinition = "integer")
  private Long usn;

  @Column(name = "common")
  private byte[] common;

  @Column(name = "kind")
  private byte[] kind;
}
