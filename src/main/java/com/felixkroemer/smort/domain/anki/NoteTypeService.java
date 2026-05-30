package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.infrastructure.sqlite.anki.NoteRepository;
import com.felixkroemer.smort.infrastructure.sqlite.anki.NoteTypeEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoteTypeService {

    private final NoteRepository noteRepository;
    
    public Map<Long, NoteTypeEntity> getNoteTypes(UUID analysisId) {
        var noteTypes = noteRepository.getNoteTypes(analysisId);
        return noteTypes.stream().collect(Collectors.toMap(nt -> nt.getId(), Function.identity()));
    }
    
}
