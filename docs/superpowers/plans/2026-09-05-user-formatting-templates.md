# User-Defined Formatting Templates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user store named formatting templates and select a default template, exposed via `/user/settings` and `/user/settings/templates`.

**Architecture:** A `UserSettings` domain aggregate bundles the scalar default-template id with a list of formatting templates (system templates merged in with a `SYSTEM`/`USER` source), composed transparently like `BulkFormat` is into `Analysis`/`Deck`. Persistence uses the single `common-table` under a shared `USER#<id>` partition with `SETTINGS#` and `TEMPLATE#<id>` sort keys.

**Tech Stack:** Java 17, Spring Boot, DynamoDB Enhanced Client, Lombok, MapStruct.

## Global Constraints

- The current user is the hardcoded dummy user `"default"` (no auth) — never pass a userId from the controller.
- No tests are written (per AGENTS.md / spec). Implementation subagents must NOT run the build (`./mvnw compile`, `./mvnw test`, etc.) — the human owns compilation. Note in each task's commit that compilation was skipped.
- Follow existing code conventions: Lombok `@Getter/@Setter/@NoArgsConstructor/@AllArgsConstructor` on aggregates; `@DynamoDbBean` entities; MapStruct mappers with `componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR`; `NotFoundException` for 404.
- System templates are uneditable (they exist only in the enum, never as persisted items) and are never deletable.
- The default template id is never null; a fresh user's default is the main system template id (`SystemFormattingTemplate.DEFAULT.id()`).
- Deleting a user template does NOT touch the default-template reference (lazy fallback is deferred to a later step).
- Work on branch `feat/user-formatting-templates`; commit after each task; leave `main` untouched.

---

### Task 1: Domain model

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/domain/user/SystemFormattingTemplate.java`
- Create: `src/main/java/com/felixkroemer/smort/domain/user/TemplateSource.java`
- Create: `src/main/java/com/felixkroemer/smort/domain/user/FormattingTemplate.java`
- Create: `src/main/java/com/felixkroemer/smort/domain/user/UserSettings.java`

**Interfaces:**
- Produces:
  - `SystemFormattingTemplate` enum with `DEFAULT` member exposing `id()`, `name()`, `content()`.
  - `enum TemplateSource { SYSTEM, USER }`.
  - `record FormattingTemplate(String id, String name, String content, TemplateSource source)`.
  - `class UserSettings` (Lombok `@Getter/@Setter/@NoArgsConstructor/@AllArgsConstructor`) with `String defaultTemplateId` and `List<FormattingTemplate> templates`.

- [ ] **Step 1: Create the system template enum**

`SystemFormattingTemplate.DEFAULT` content equals the built-in formatting rules (`ChatUtil.formattingRules()`), referenced directly so there is no duplicated source of truth.

```java
package com.felixkroemer.smort.domain.user;

import com.felixkroemer.smort.domain.chat.ChatUtil;

public enum SystemFormattingTemplate {

  DEFAULT("DEFAULT", "Default", ChatUtil.formattingRules());

  private final String id;
  private final String name;
  private final String content;

  SystemFormattingTemplate(String id, String name, String content) {
    this.id = id;
    this.name = name;
    this.content = content;
  }

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String content() {
    return content;
  }
}
```

- [ ] **Step 2: Create the source enum**

```java
package com.felixkroemer.smort.domain.user;

public enum TemplateSource {
  SYSTEM,
  USER
}
```

- [ ] **Step 3: Create the template value object**

```java
package com.felixkroemer.smort.domain.user;

public record FormattingTemplate(
    String id, String name, String content, TemplateSource source) {}
```

- [ ] **Step 4: Create the settings aggregate**

```java
package com.felixkroemer.smort.domain.user;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSettings {
  private String defaultTemplateId;
  private List<FormattingTemplate> templates = List.of();
}
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/user/
git commit -m "feat: add user settings domain model and system template enum"
```

---

### Task 2: Persistence — keys, entities, table beans

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/keys/partition/UserKeys.java`
- Create: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/keys/sort/UserSettingsKeys.java`
- Create: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/user/UserSettingsEntity.java`
- Create: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/user/UserFormattingTemplateEntity.java`
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/DynamoDbClientConfig.java` (add two table beans + imports)

**Interfaces:**
- Consumes: `SystemFormattingTemplate.DEFAULT.id()` (Task 1).
- Produces:
  - `UserKeys.userPk(String userId)` → `"USER#" + userId`.
  - `UserSettingsKeys.settingsSk()` → `"SETTINGS#"`, `UserSettingsKeys.templateSk(String templateId)` → `"TEMPLATE#" + templateId`, `UserSettingsKeys.templatePrefix()` → `"TEMPLATE#"`.
  - `UserSettingsEntity` (getters/setters: `pk`, `sk`, `defaultTemplateId`), constructor `UserSettingsEntity(String userId)` defaulting `defaultTemplateId` to the main system template id.
  - `UserFormattingTemplateEntity` (getters/setters: `pk`, `sk`, `templateId`, `name`, `content`), constructor `UserFormattingTemplateEntity(String userId, UUID templateId, String name, String content)`.
  - `DynamoDbTable<UserSettingsEntity> userSettingsTable` and `DynamoDbTable<UserFormattingTemplateEntity> userFormattingTemplateTable` beans.

- [ ] **Step 1: Create the partition key helper**

```java
package com.felixkroemer.smort.infrastructure.dynamodb.keys.partition;

public final class UserKeys {

  public static String userPk(String userId) {
    return "USER#" + userId;
  }
}
```

- [ ] **Step 2: Create the sort key helper**

```java
package com.felixkroemer.smort.infrastructure.dynamodb.keys.sort;

public final class UserSettingsKeys {

  public static String settingsSk() {
    return "SETTINGS#";
  }

  public static String templateSk(String templateId) {
    return "TEMPLATE#" + templateId;
  }

  public static String templatePrefix() {
    return "TEMPLATE#";
  }
}
```

- [ ] **Step 3: Create `UserSettingsEntity`**

```java
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
```

- [ ] **Step 4: Create `UserFormattingTemplateEntity`**

```java
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
```

- [ ] **Step 5: Register the table beans in `DynamoDbClientConfig`**

Add two imports after the existing `chat.ChatMessageEntity` import:

```java
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserFormattingTemplateEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserSettingsEntity;
```

Add two beans (e.g. after the `analysisMetaTable` bean):

```java
@Bean
DynamoDbTable<UserSettingsEntity> userSettingsTable(DynamoDbEnhancedClient enhancedClient) {
  return enhancedClient.table(COMMON_TABLE_NAME, TableSchema.fromBean(UserSettingsEntity.class));
}

@Bean
DynamoDbTable<UserFormattingTemplateEntity> userFormattingTemplateTable(
    DynamoDbEnhancedClient enhancedClient) {
  return enhancedClient.table(
      COMMON_TABLE_NAME, TableSchema.fromBean(UserFormattingTemplateEntity.class));
}
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/
git commit -m "feat: add user settings and formatting template DynamoDB entities"
```

---

### Task 3: Repositories

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/user/UserSettingsRepository.java`
- Create: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/user/UserFormattingTemplateRepository.java`

**Interfaces:**
- Consumes: `UserSettingsEntity`/`UserFormattingTemplateEntity` and key helpers (Task 2).
- Produces:
  - `UserSettingsRepository.findByUserId(String userId)` → `Optional<UserSettingsEntity>`; `save(UserSettingsEntity)`.
  - `UserFormattingTemplateRepository.findByUserId(String userId)` → `List<UserFormattingTemplateEntity>`; `findByUserIdAndTemplateId(String userId, String templateId)` → `Optional<UserFormattingTemplateEntity>`; `save(UserFormattingTemplateEntity)`; `delete(String userId, String templateId)`.

- [ ] **Step 1: Create `UserSettingsRepository`**

```java
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
```

- [ ] **Step 2: Create `UserFormattingTemplateRepository`**

```java
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
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/user/
git commit -m "feat: add user settings and formatting template repositories"
```

---

### Task 4: Entity mappers

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/domain/user/mapping/FormattingTemplateEntityMapper.java`
- Create: `src/main/java/com/felixkroemer/smort/domain/user/mapping/UserSettingsEntityMapper.java`

**Interfaces:**
- Consumes: entities (Task 2), domain types (Task 1).
- Produces:
  - `FormattingTemplateEntityMapper.toFormattingTemplate(UserFormattingTemplateEntity)` → `FormattingTemplate` with `source = USER`.
  - `UserSettingsEntityMapper.toUserSettings(UserSettingsEntity, List<FormattingTemplate>)` → `UserSettings`.

- [ ] **Step 1: Create the per-item entity mapper**

```java
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
```

- [ ] **Step 2: Create the aggregate mapper**

```java
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
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/user/mapping/
git commit -m "feat: add user settings entity mappers"
```

---

### Task 5: UserSettingsService

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/domain/user/UserSettingsService.java`

**Interfaces:**
- Consumes: `SystemFormattingTemplate`, `TemplateSource`, `FormattingTemplate`, `UserSettings` (Task 1); `UserSettingsRepository`, `UserFormattingTemplateRepository` (Task 3); `FormattingTemplateEntityMapper`, `UserSettingsEntityMapper` (Task 4); `NotFoundException`.
- Produces (called by the controller in Task 7):
  - `UserSettings getUserSettings()`
  - `UserSettings updateUserSettings(Optional<String> defaultTemplateId)`
  - `FormattingTemplate createTemplate(String name, String content)`
  - `FormattingTemplate updateTemplate(String id, String name, String content)`
  - `void deleteTemplate(String id)`

- [ ] **Step 1: Implement the service**

```java
package com.felixkroemer.smort.domain.user;

import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.domain.user.mapping.FormattingTemplateEntityMapper;
import com.felixkroemer.smort.domain.user.mapping.UserSettingsEntityMapper;
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserFormattingTemplateEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserFormattingTemplateRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserSettingsEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserSettingsRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
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
        userFormattingTemplateRepository.findByUserId(CURRENT_USER).stream()
            .map(formattingTemplateEntityMapper::toFormattingTemplate)
            .toList();
    var systemTemplates =
        Arrays.stream(SystemFormattingTemplate.values())
            .map(s -> new FormattingTemplate(s.id(), s.name(), s.content(), TemplateSource.SYSTEM))
            .toList();
    return userSettingsEntityMapper.toUserSettings(
        settings, Stream.concat(systemTemplates.stream(), userTemplates.stream()).toList());
  }

  public UserSettings updateUserSettings(Optional<String> defaultTemplateId) {
    var settings =
        userSettingsRepository
            .findByUserId(CURRENT_USER)
            .orElseGet(() -> new UserSettingsEntity(CURRENT_USER));
    if (defaultTemplateId != null) {
      settings.setDefaultTemplateId(
          defaultTemplateId.orElse(SystemFormattingTemplate.DEFAULT.id()));
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
    var entity = getTemplate(id);
    entity.setName(name);
    entity.setContent(content);
    userFormattingTemplateRepository.save(entity);
    return formattingTemplateEntityMapper.toFormattingTemplate(entity);
  }

  public void deleteTemplate(String id) {
    getTemplate(id);
    userFormattingTemplateRepository.delete(CURRENT_USER, id);
  }

  private UserFormattingTemplateEntity getTemplate(String id) {
    return userFormattingTemplateRepository
        .findByUserIdAndTemplateId(CURRENT_USER, id)
        .orElseThrow(
            () -> new NotFoundException("Could not find formatting template. id={}", id));
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/user/UserSettingsService.java
git commit -m "feat: add UserSettingsService for templates and default selection"
```

---

### Task 6: DTOs and REST mapper

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/application/user/dto/UserSettingsResponse.java`
- Create: `src/main/java/com/felixkroemer/smort/application/user/dto/FormattingTemplateResponse.java`
- Create: `src/main/java/com/felixkroemer/smort/application/user/dto/UpdateUserSettingsRequest.java`
- Create: `src/main/java/com/felixkroemer/smort/application/user/dto/CreateFormattingTemplateRequest.java`
- Create: `src/main/java/com/felixkroemer/smort/application/user/dto/UpdateFormattingTemplateRequest.java`
- Create: `src/main/java/com/felixkroemer/smort/application/user/mapping/UserSettingsRestMapper.java`

**Interfaces:**
- Consumes: `UserSettings`, `FormattingTemplate`, `TemplateSource` (Task 1).
- Produces:
  - `record UserSettingsResponse(String defaultTemplateId, List<FormattingTemplateResponse> templates)`
  - `record FormattingTemplateResponse(String id, String name, String content, TemplateSource type)`
  - `record UpdateUserSettingsRequest(Optional<String> defaultTemplateId)`
  - `record CreateFormattingTemplateRequest(String name, String content)`
  - `record UpdateFormattingTemplateRequest(String name, String content)`
  - `UserSettingsRestMapper.toUserSettingsResponse(UserSettings)` and `UserSettingsRestMapper.toFormattingTemplateResponse(FormattingTemplate)`.

- [ ] **Step 1: Create the DTOs**

`UserSettingsResponse.java`:
```java
package com.felixkroemer.smort.application.user.dto;

import java.util.List;

public record UserSettingsResponse(
    String defaultTemplateId, List<FormattingTemplateResponse> templates) {}
```

`FormattingTemplateResponse.java`:
```java
package com.felixkroemer.smort.application.user.dto;

import com.felixkroemer.smort.domain.user.TemplateSource;

public record FormattingTemplateResponse(
    String id, String name, String content, TemplateSource type) {}
```

`UpdateUserSettingsRequest.java`:
```java
package com.felixkroemer.smort.application.user.dto;

import java.util.Optional;

public record UpdateUserSettingsRequest(Optional<String> defaultTemplateId) {}
```

`CreateFormattingTemplateRequest.java`:
```java
package com.felixkroemer.smort.application.user.dto;

public record CreateFormattingTemplateRequest(String name, String content) {}
```

`UpdateFormattingTemplateRequest.java`:
```java
package com.felixkroemer.smort.application.user.dto;

public record UpdateFormattingTemplateRequest(String name, String content) {}
```

- [ ] **Step 2: Create the REST mapper**

`type` is mapped from the domain `source`; the `TemplateSource` enum is used directly in the response.

```java
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
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/application/user/
git commit -m "feat: add user settings DTOs and REST mapper"
```

---

### Task 7: UserSettingsController

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/application/user/UserSettingsController.java`

**Interfaces:**
- Consumes: `UserSettingsService` (Task 5), `UserSettingsRestMapper` + DTOs (Task 6).
- Produces (final REST surface):
  - `GET /user/settings` → `UserSettingsResponse`
  - `PATCH /user/settings` → `UserSettingsResponse`
  - `POST /user/settings/templates` → `FormattingTemplateResponse`
  - `PUT /user/settings/templates/{id}` → `FormattingTemplateResponse`
  - `DELETE /user/settings/templates/{id}` → `204`

- [ ] **Step 1: Implement the controller**

```java
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
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/application/user/UserSettingsController.java
git commit -m "feat: add user settings and formatting template endpoints"
```

---

## Self-Review Notes

- **Spec coverage:** all spec sections map to a task — domain model (T1), persistence/keys/entities/beans (T2), repositories (T3), entity mappers (T4), service composition + CRUD (T5), DTOs/REST mapper (T6), controller/REST surface (T7). Lazy default fallback is explicitly deferred (per spec) and not implemented.
- **Type consistency:** `TemplateSource` reused in domain and response DTO; `FormattingTemplate` used consistently across service, mappers, DTOs. `UserSettingsEntityMapper.toUserSettings(UserSettingsEntity, List<FormattingTemplate>)` matches Task 5's call. `UserSettingsService` method names match the controller calls in Task 7.
- **No placeholders:** every code step contains full, compilable code.