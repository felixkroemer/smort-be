package com.felixkroemer.smort.domain.user;

import com.felixkroemer.smort.domain.anki.AnalysisSettings;
import com.felixkroemer.smort.domain.deck.DeckSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FormattingSettingsResolver {

  private final UserSettingsService userSettingsService;

  public String resolve(DeckSettings settings) {
    return switch (settings.formattingMode()) {
      case DEFAULT -> userSettingsService.getDefaultTemplateContent();
      case TEMPLATE -> userSettingsService.getTemplateContent(settings.templateId());
      case CUSTOM -> settings.formatInstructions();
    };
  }

  public String resolve(AnalysisSettings settings) {
    return switch (settings.formattingMode()) {
      case DEFAULT -> userSettingsService.getDefaultTemplateContent();
      case TEMPLATE -> userSettingsService.getTemplateContent(settings.templateId());
      case CUSTOM -> settings.formatInstructions();
    };
  }
}