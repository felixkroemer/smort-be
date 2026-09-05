package com.felixkroemer.smort.domain.user.mapping;

import com.felixkroemer.smort.domain.user.FormattingTemplate;
import com.felixkroemer.smort.domain.user.UserSettings;
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserSettingsEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserSettingsEntityMapper {

  UserSettings toUserSettings(UserSettingsEntity settings, List<FormattingTemplate> templates);
}
