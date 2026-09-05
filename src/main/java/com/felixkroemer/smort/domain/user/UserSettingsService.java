package com.felixkroemer.smort.domain.user;

import com.felixkroemer.smort.common.exception.LogSeverity;
import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.common.exception.SmortException;
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
import org.springframework.http.HttpStatus;
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
        getTemplates().stream()
            .map(formattingTemplateEntityMapper::toFormattingTemplate)
            .toList();
    var systemTemplates =
        Arrays.stream(SystemFormattingTemplate.values())
            .map(s -> new FormattingTemplate(s.getId(), s.getName(), s.getContent(), TemplateSource.SYSTEM))
            .toList();
    return userSettingsEntityMapper.toUserSettings(
        settings, Stream.concat(systemTemplates.stream(), userTemplates.stream()).toList());
  }

  public String getTemplateContent(String templateId) {
    return getTemplateContent(getUserSettings(), templateId);
  }

  public String getDefaultTemplateContent() {
    var userSettings = getUserSettings();
    return getTemplateContent(userSettings, userSettings.getDefaultTemplateId());
  }

  private String getTemplateContent(UserSettings userSettings, String templateId) {
    return userSettings.getTemplates().stream()
        .filter(t -> t.id().equals(templateId))
        .findFirst()
        .map(FormattingTemplate::content)
        .orElseThrow(
            () ->
                new NotFoundException(
                    "Referenced formatting template does not exist. Please choose a valid template. id={}",
                    templateId));
  }

  public UserSettings updateUserSettings(String defaultTemplateId) {
    var settings =
        userSettingsRepository
            .findByUserId(CURRENT_USER)
            .orElseGet(() -> new UserSettingsEntity(CURRENT_USER));
    if (defaultTemplateId != null) {
      settings.setDefaultTemplateId(
          defaultTemplateId.isBlank()
              ? SystemFormattingTemplate.DEFAULT.getId()
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
    if (SystemFormattingTemplate.fromId(id).isPresent()) {
      throw new SmortException(
          HttpStatus.CONFLICT,
          LogSeverity.INFO,
          "Cannot update a system formatting template. id={}",
          id);
    }
    var entity = getTemplate(id);
    entity.setName(name);
    entity.setContent(content);
    userFormattingTemplateRepository.save(entity);
    return formattingTemplateEntityMapper.toFormattingTemplate(entity);
  }

  public void deleteTemplate(String id) {
    if (SystemFormattingTemplate.fromId(id).isPresent()) {
      throw new SmortException(
          HttpStatus.CONFLICT,
          LogSeverity.INFO,
          "Cannot delete a system formatting template. id={}",
          id);
    }
    if (userFormattingTemplateRepository.findByUserIdAndTemplateId(CURRENT_USER, id).isEmpty()) {
      throw new NotFoundException("Could not find formatting template. id={}", id);
    }
    var settings = getSettingsMeta();
    if (settings.getDefaultTemplateId().equals(id)) {
      throw new SmortException(
          HttpStatus.CONFLICT,
          LogSeverity.INFO,
          "Cannot delete the default formatting template. id={}",
          id);
    } else {
      userFormattingTemplateRepository.delete(CURRENT_USER, id);
    }
  }

  private UserFormattingTemplateEntity getTemplate(String id) {
    return userFormattingTemplateRepository
        .findByUserIdAndTemplateId(CURRENT_USER, id)
        .orElseThrow(
            () -> new NotFoundException("Could not find formatting template. id={}", id));
  }

  private List<UserFormattingTemplateEntity> getTemplates() {
    return userFormattingTemplateRepository.findByUserId(CURRENT_USER);
  }

  private UserSettingsEntity getSettingsMeta() {
    return userSettingsRepository
        .findByUserId(CURRENT_USER)
        .orElseThrow(
            () -> new NotFoundException("Could not find user settings. userId={}", CURRENT_USER));
  }
}
