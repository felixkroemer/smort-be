package com.felixkroemer.smort.domain.user;

import com.felixkroemer.smort.common.exception.NotFoundException;
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
      case DEFAULT ->
          resolveTemplateContent(userSettingsService.getUserSettings().getDefaultTemplateId());
      case TEMPLATE -> resolveTemplateContent(settings.templateId());
      case CUSTOM -> settings.formatInstructions();
    };
  }

  public String resolve(AnalysisSettings settings) {
    return switch (settings.formattingMode()) {
      case DEFAULT ->
          resolveTemplateContent(userSettingsService.getUserSettings().getDefaultTemplateId());
      case TEMPLATE -> resolveTemplateContent(settings.templateId());
      case CUSTOM -> settings.formatInstructions();
    };
  }

  private String resolveTemplateContent(String templateId) {
    return userSettingsService.getUserSettings().getTemplates().stream()
        .filter(t -> t.id().equals(templateId))
        .findFirst()
        .map(FormattingTemplate::content)
        .orElseThrow(
            () ->
                new NotFoundException(
                    "Referenced formatting template does not exist. Please choose a valid template. id={}",
                    templateId));
  }
}