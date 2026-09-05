package com.felixkroemer.smort.infrastructure.dynamodb.user;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.UserKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.UserSettingsKeys;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

@Repository
@RequiredArgsConstructor
public class UserSettingsRepository {

  private final DynamoDbTable<UserSettingsEntity> userSettingsTable;

  public Optional<UserSettingsEntity> findByUserId(String userId) {
    var key =
        Key.builder()
            .partitionValue(UserKeys.userPk(userId))
            .sortValue(UserSettingsKeys.settingsSk())
            .build();
    return Optional.ofNullable(userSettingsTable.getItem(key));
  }

  public void save(UserSettingsEntity entity) {
    userSettingsTable.putItem(entity);
  }
}
