package com.felixkroemer.smort.infrastructure.dynamodb.user;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.UserKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.UserSettingsKeys;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
public class UserFormattingTemplateEntity {

  @Getter(onMethod_ = @DynamoDbPartitionKey)
  private String pk;

  @Getter(onMethod_ = @DynamoDbSortKey)
  private String sk;

  private String templateId;
  private String name;
  private String content;

  public UserFormattingTemplateEntity(
      String userId, UUID templateId, String name, String content) {
    this.pk = UserKeys.userPk(userId);
    this.sk = UserSettingsKeys.templateSk(templateId.toString());
    this.templateId = templateId.toString();
    this.name = name;
    this.content = content;
  }
}
