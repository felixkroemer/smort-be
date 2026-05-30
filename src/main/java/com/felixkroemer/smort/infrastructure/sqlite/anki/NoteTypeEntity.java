package com.felixkroemer.smort.infrastructure.sqlite.anki;

import jakarta.persistence.*;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "notetypes")
@Getter
@Immutable
@NoArgsConstructor
public class NoteTypeEntity {

  @Id
  @Column(columnDefinition = "integer")
  private Long id;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "fields", joinColumns = @JoinColumn(name = "ntid"))
  @Column(name = "name")
  private List<String> fields;

  String name;
}
