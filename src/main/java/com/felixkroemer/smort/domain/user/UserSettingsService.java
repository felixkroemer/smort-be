package com.felixkroemer.smort.domain.user;

import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.domain.user.mapping.FormattingTemplateEntityMapper;
import com.felixkroemer.smort.domain.user.mapping.UserSettingsEntityMapper;
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserFormattingTemplateEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserFormattingTemplateRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserSettingsEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserSettingsRepository;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

  private static final String CURRENT_USER = "default";

  private final UserSettingsRepository userSettingsRepository;
  private final UserFormattingTemplateRepository userFormattingTemplateRepository;
  private final UserSettingsEntityMapper userSettingsEntityMapper;
  private final FormattingTemplateEntityMapper formattingTemplateEntityMapper;

  public UserSettings getUserSettings() {
    var settings =
        userSettingsRepository
            .findByUserId(CURRENT_USER)
            .orElseGet(() -> new UserSettingsEntity(CURRENT_USER));
    var userTemplates =
        userFormattingTemplateRepository.findByUserId(CURRENT_USER).stream()
            .map(formattingTemplateEntityMapper::toFormattingTemplate)
            .toList();
    var systemTemplates =
        Arrays.stream(SystemFormattingTemplate.values())
            .map(s -> new FormattingTemplate(s.id(), s.name(), s.content(), TemplateSource.SYSTEM))
            .toList();
    return userSettingsEntityMapper.toUserSettings(
        settings, Stream.concat(systemTemplates.stream(), userTemplates.stream()).toList());
  }

  public UserSettings updateUserSettings(String defaultTemplateId) {
    var settings =
        userSettingsRepository
            .findByUserId(CURRENT_USER)
            .orElseGet(() -> new UserSettingsEntity(CURRENT_USER));
    if (defaultTemplateId != null) {
      settings.setDefaultTemplateId(
          defaultTemplateId.isBlank()
              ? SystemFormattingTemplate.DEFAULT.id()
              : defaultTemplateId);
      userSettingsRepository.save(settings);
    }
    return getUserSettings();
  }

  public FormattingTemplate createTemplate(String name, String content) {
    var entity =
        new UserFormattingTemplateEntity(CURRENT_USER, UUID.randomUUID(), name, content);
    userFormattingTemplateRepository.save(entity);
    return new FormattingTemplate(
        entity.getTemplateId(), name, content, TemplateSource.USER);
  }

  public FormattingTemplate updateTemplate(String id, String name, String content) {
    var entity = getTemplate(id);
    entity.setName(name);
    entity.setContent(content);
    userFormattingTemplateRepository.save(entity);
    return formattingTemplateEntityMapper.toFormattingTemplate(entity);
  }

  public void deleteTemplate(String id) {
    getTemplate(id);
    userFormattingTemplateRepository.delete(CURRENT_USER, id);
  }

  private UserFormattingTemplateEntity getTemplate(String id) {
    return userFormattingTemplateRepository
        .findByUserIdAndTemplateId(CURRENT_USER, id)
        .orElseThrow(
            () -> new NotFoundException("Could not find formatting template. id={}", id));
  }
}
