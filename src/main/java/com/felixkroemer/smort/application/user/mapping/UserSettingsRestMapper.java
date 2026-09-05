package com.felixkroemer.smort.application.user.mapping;

import com.felixkroemer.smort.application.user.dto.FormattingTemplateResponse;
import com.felixkroemer.smort.application.user.dto.UserSettingsResponse;
import com.felixkroemer.smort.domain.user.FormattingTemplate;
import com.felixkroemer.smort.domain.user.UserSettings;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserSettingsRestMapper {

  UserSettingsResponse toUserSettingsResponse(UserSettings settings);

  @Mapping(source = "source", target = "type")
  FormattingTemplateResponse toFormattingTemplateResponse(FormattingTemplate template);
}
