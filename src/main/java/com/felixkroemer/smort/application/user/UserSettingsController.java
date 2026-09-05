package com.felixkroemer.smort.application.user;

import com.felixkroemer.smort.application.user.dto.CreateFormattingTemplateRequest;
import com.felixkroemer.smort.application.user.dto.FormattingTemplateResponse;
import com.felixkroemer.smort.application.user.dto.UpdateFormattingTemplateRequest;
import com.felixkroemer.smort.application.user.dto.UpdateUserSettingsRequest;
import com.felixkroemer.smort.application.user.dto.UserSettingsResponse;
import com.felixkroemer.smort.application.user.mapping.UserSettingsRestMapper;
import com.felixkroemer.smort.domain.user.UserSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("user/settings")
public class UserSettingsController {

  private final UserSettingsService userSettingsService;
  private final UserSettingsRestMapper userSettingsRestMapper;

  @GetMapping
  public UserSettingsResponse getUserSettings() {
    return userSettingsRestMapper.toUserSettingsResponse(userSettingsService.getUserSettings());
  }

  @PatchMapping
  public UserSettingsResponse updateUserSettings(
      @RequestBody UpdateUserSettingsRequest request) {
    return userSettingsRestMapper.toUserSettingsResponse(
        userSettingsService.updateUserSettings(request.defaultTemplateId()));
  }

  @PostMapping("/templates")
  public FormattingTemplateResponse createTemplate(
      @RequestBody CreateFormattingTemplateRequest request) {
    return userSettingsRestMapper.toFormattingTemplateResponse(
        userSettingsService.createTemplate(request.name(), request.content()));
  }

  @PutMapping("/templates/{id}")
  public FormattingTemplateResponse updateTemplate(
      @PathVariable("id") String id, @RequestBody UpdateFormattingTemplateRequest request) {
    return userSettingsRestMapper.toFormattingTemplateResponse(
        userSettingsService.updateTemplate(id, request.name(), request.content()));
  }

  @DeleteMapping("/templates/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteTemplate(@PathVariable("id") String id) {
    userSettingsService.deleteTemplate(id);
  }
}
