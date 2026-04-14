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
public class NoteEntity {

  @Id
  @Column(columnDefinition = "integer")
  private Long id;

  @OneToMany(mappedBy = "note")
  private List<CardEntity> cards;

  @Convert(converter = FldsConverter.class)
  @Column(name = "flds")
  private List<String> flds;
}
