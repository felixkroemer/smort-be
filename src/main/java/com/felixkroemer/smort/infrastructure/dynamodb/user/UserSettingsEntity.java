package com.felixkroemer.smort.infrastructure.dynamodb.user;

import com.felixkroemer.smort.domain.user.SystemFormattingTemplate;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.UserKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.UserSettingsKeys;
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
public class UserSettingsEntity {

  @Getter(onMethod_ = @DynamoDbPartitionKey)
  private String pk;

  @Getter(onMethod_ = @DynamoDbSortKey)
  private String sk;

  private String defaultTemplateId;

  public UserSettingsEntity(String userId) {
    this.pk = UserKeys.userPk(userId);
    this.sk = UserSettingsKeys.settingsSk();
    this.defaultTemplateId = SystemFormattingTemplate.DEFAULT.id();
  }
}
