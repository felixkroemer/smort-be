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

  @Column(name = "ord", columnDefinition = "integer")
  private Long ord;

  @Column(name = "mod", columnDefinition = "integer")
  private Long mod;

  @Column(name = "usn", columnDefinition = "integer")
  private Long usn;

  @Column(name = "type", columnDefinition = "integer")
  private Long type;

  @Column(name = "queue", columnDefinition = "integer")
  private Long queue;

  @Column(name = "due", columnDefinition = "integer")
  private Long due;

  @Column(name = "ivl", columnDefinition = "integer")
  private Long ivl;

  @Column(name = "factor", columnDefinition = "integer")
  private Long factor;

  @Column(name = "reps", columnDefinition = "integer")
  private Long reps;

  @Column(name = "lapses", columnDefinition = "integer")
  private Long lapses;

  @Column(name = "\"left\"", columnDefinition = "integer")
  private Long left;

  @Column(name = "odue", columnDefinition = "integer")
  private Long odue;

  @Column(name = "odid", columnDefinition = "integer")
  private Long odid;

  @Column(name = "flags", columnDefinition = "integer")
  private Long flags;

  @Column(name = "data")
  private String data;
}
