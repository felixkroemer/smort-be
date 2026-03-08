package com.felixkroemer.smort.infrastructure.sqlite.anki;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Getter
@Immutable
@Table(name = "cards")
public class AnkiCardEntity {

  @Id
  @Column(columnDefinition = "integer")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "nid", columnDefinition = "integer")
  private AnkiNoteEntity note;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "did", columnDefinition = "integer")
  private AnkiDeckEntity deck;
}
