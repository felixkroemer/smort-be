package com.felixkroemer.smort.domain.user;

import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.domain.anki.AnalysisSettings;
import com.felixkroemer.smort.domain.deck.DeckSettings;
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserFormattingTemplateEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserFormattingTemplateRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserSettingsEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FormattingSettingsResolver {

  private static final String CURRENT_USER = "default";

  private final UserFormattingTemplateRepository userFormattingTemplateRepository;
  private final UserSettingsRepository userSettingsRepository;

  public String resolve(DeckSettings settings) {
    return switch (settings.formattingMode()) {
      case DEFAULT -> resolveTemplateContent(getDefaultTemplateId());
      case TEMPLATE -> resolveTemplateContent(settings.templateId());
      case CUSTOM -> settings.formatInstructions();
    };
  }

  public String resolve(AnalysisSettings settings) {
    return switch (settings.formattingMode()) {
      case DEFAULT -> resolveTemplateContent(getDefaultTemplateId());
      case TEMPLATE -> resolveTemplateContent(settings.templateId());
      case CUSTOM -> settings.formatInstructions();
    };
  }

  private String getDefaultTemplateId() {
    return userSettingsRepository
        .findByUserId(CURRENT_USER)
        .map(UserSettingsEntity::getDefaultTemplateId)
        .orElseThrow(
            () -> new NotFoundException("Could not find user settings. userId={}", CURRENT_USER));
  }

  private String resolveTemplateContent(String templateId) {
    var systemTemplate = SystemFormattingTemplate.fromId(templateId);
    if (systemTemplate.isPresent()) {
      return systemTemplate.get().getContent();
    }
    return userFormattingTemplateRepository
        .findByUserIdAndTemplateId(CURRENT_USER, templateId)
        .map(UserFormattingTemplateEntity::getContent)
        .orElseThrow(
            () ->
                new NotFoundException(
                    "Referenced formatting template does not exist. Please choose a valid template. id={}",
                    templateId));
  }
}
