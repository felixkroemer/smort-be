package com.felixkroemer.smort.infrastructure.sqlite.anki;

import jakarta.persistence.*;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "notes")
@Getter
@Immutable
@NoArgsConstructor
public class AnkiNoteEntity {

  @Id
  @Column(columnDefinition = "integer")
  private Long id;

  @OneToMany(mappedBy = "note")
  private List<AnkiCardEntity> cards;

  @Column(name = "guid")
  private String guid;

  @Column(name = "mid", columnDefinition = "integer")
  private Long mid;

  @Column(name = "mod", columnDefinition = "integer")
  private Long mod;

  @Column(name = "usn", columnDefinition = "integer")
  private Long usn;

  @Column(name = "tags")
  private String tags;

  @Column(name = "flds")
  private String flds;

  @Column(name = "sfld", columnDefinition = "integer")
  private Long sfld;

  @Column(name = "csum", columnDefinition = "integer")
  private Long csum;

  @Column(name = "flags", columnDefinition = "integer")
  private Long flags;

  @Column(name = "data")
  private String data;
}
