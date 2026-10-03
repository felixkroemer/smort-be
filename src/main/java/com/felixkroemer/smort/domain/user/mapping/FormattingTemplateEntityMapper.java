package com.felixkroemer.smort.domain.user.mapping;

import com.felixkroemer.smort.domain.user.FormattingTemplate;
import com.felixkroemer.smort.domain.user.TemplateSource;
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserFormattingTemplateEntity;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FormattingTemplateEntityMapper {

  public FormattingTemplate toFormattingTemplate(UserFormattingTemplateEntity entity) {
    return new FormattingTemplate(
        entity.getTemplateId(), entity.getName(), entity.getContent(), TemplateSource.USER);
  }

  public List<FormattingTemplate> toFormattingTemplate(List<UserFormattingTemplateEntity> entities) {
    return entities.stream().map(this::toFormattingTemplate).toList();
  }
}
