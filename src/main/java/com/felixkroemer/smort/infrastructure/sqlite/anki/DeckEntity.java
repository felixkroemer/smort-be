package com.felixkroemer.smort.infrastructure.sqlite.anki;

import jakarta.persistence.*;
import java.util.List;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Getter
@Immutable
@Table(name = "decks")
public class DeckEntity {

  @Id
  @Column(columnDefinition = "integer")
  private Long id;

  @OneToMany(mappedBy = "deck")
  private List<CardEntity> cards;

  @Column(name = "name")
  private String name;
}
