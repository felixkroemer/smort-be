package com.felixkroemer.smort.domain.user.mapping;

import com.felixkroemer.smort.domain.user.FormattingTemplate;
import com.felixkroemer.smort.domain.user.TemplateSource;
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserFormattingTemplateEntity;
import org.springframework.stereotype.Component;

@Component
public class FormattingTemplateEntityMapper {

  public FormattingTemplate toFormattingTemplate(UserFormattingTemplateEntity entity) {
    return new FormattingTemplate(
        entity.getTemplateId(), entity.getName(), entity.getContent(), TemplateSource.USER);
  }
}
