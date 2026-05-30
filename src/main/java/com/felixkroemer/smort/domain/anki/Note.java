package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.infrastructure.sqlite.anki.CardEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class Note {
    private Long id;

    private List<CardEntity> cards;
    
    private Map<String, String> flds;

    private String guid;
}
