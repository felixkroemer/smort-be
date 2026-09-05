package com.felixkroemer.smort.infrastructure.dynamodb.user;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.UserKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.UserSettingsKeys;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

@Repository
@RequiredArgsConstructor
public class UserFormattingTemplateRepository {

  private final DynamoDbTable<UserFormattingTemplateEntity> userFormattingTemplateTable;

  public List<UserFormattingTemplateEntity> findByUserId(String userId) {
    var condition =
        QueryConditional.sortBeginsWith(
            Key.builder()
                .partitionValue(UserKeys.userPk(userId))
                .sortValue(UserSettingsKeys.templatePrefix())
                .build());
    return userFormattingTemplateTable.query(condition).items().stream().toList();
  }

  public Optional<UserFormattingTemplateEntity> findByUserIdAndTemplateId(
      String userId, String templateId) {
    var key =
        Key.builder()
            .partitionValue(UserKeys.userPk(userId))
            .sortValue(UserSettingsKeys.templateSk(templateId))
            .build();
    return Optional.ofNullable(userFormattingTemplateTable.getItem(key));
  }

  public void save(UserFormattingTemplateEntity entity) {
    userFormattingTemplateTable.putItem(entity);
  }

  public void delete(String userId, String templateId) {
    var key =
        Key.builder()
            .partitionValue(UserKeys.userPk(userId))
            .sortValue(UserSettingsKeys.templateSk(templateId))
            .build();
    userFormattingTemplateTable.deleteItem(key);
  }
}
