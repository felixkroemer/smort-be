package com.felixkroemer.smort.domain.user;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSettings {
  private String defaultTemplateId;
  private List<FormattingTemplate> templates = List.of();
}
