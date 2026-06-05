package com.felixkroemer.smort.domain.anki;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AnalysisNote {
  private Long id;

  private Map<String, String> flds;

  private String guid;
  
  private Long noteTypeId;
}
