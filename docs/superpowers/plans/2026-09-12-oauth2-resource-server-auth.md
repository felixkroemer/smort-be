# OAuth2 Resource Server Auth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the smort API require a valid Google ID token (Bearer JWT) whose `email` claim matches a hardcoded constant.

**Architecture:** Add `spring-boot-starter-oauth2-resource-server` and let Spring Boot auto-configure the `JwtDecoder` from `issuer-uri` + `audiences` properties. A `SecurityConfig` builds the filter chain with CSRF disabled and a single `anyRequest()` rule backed by an `AuthorizationManager<RequestAuthorizationContext>` that compares the token's `email` claim to the `ALLOWED_EMAIL` constant. No method annotations, no custom decoder, no custom validators, no session config.

**Tech Stack:** Spring Boot 4.0.3, Java 25, Spring Security (via `spring-boot-starter-oauth2-resource-server`), Google as OIDC issuer.

## Global Constraints

- Spring Boot parent stays `4.0.3`; Java stays `25`. Do not upgrade or downgrade.
- Add exactly one dependency: `org.springframework.boot:spring-boot-starter-oauth2-resource-server`.
- Use these property values verbatim:
  - `spring.security.oauth2.resourceserver.jwt.issuer-uri=https://accounts.google.com`
  - `spring.security.oauth2.resourceserver.jwt.audiences=${GOOGLE_OAUTH_CLIENT_ID}`
- `ALLOWED_EMAIL` is a code constant with placeholder value `your-email@example.com`. Do not invent or substitute a real email address.
- The existing CORS bean in `SmortConfig.java` stays untouched. Do not add `cors()` or `sessionManagement()` to the security chain.
- Every endpoint stays protected, including swagger and cron. No `permitAll()`.
- Do NOT write tests (AGENTS.md: tests only when explicitly requested; the spec excludes them).
- Do NOT run or fix the build (`./mvnw compile`, `./mvnw test`). The human owns compilation (AGENTS.md). Note in the report that compilation was skipped.
- Work only inside this worktree (`.worktrees/feat-oauth2-resource-server`). Commit to branch `feat/oauth2-resource-server`. Do not push or merge until the plan is complete.

---

### Task 1: Add OAuth2 resource server security

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`
- Create: `src/main/java/com/felixkroemer/smort/common/config/SecurityConfig.java`

**Interfaces:**
- Consumes: nothing (stands alone; no existing code is called).
- Produces: `SecurityFilterChain` bean and `AuthorizationManager<RequestAuthorizationContext>` bean in package `com.felixkroemer.smort.common.config`. Everything after this task is secured automatically; no other file references these beans.

- [ ] **Step 1: Add the resource-server dependency to `pom.xml`**

Insert after the `spring-boot-starter-webmvc` dependency (after line 34):

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>
```

This transitively brings in `spring-boot-starter-security`.

- [ ] **Step 2: Add JWT configuration to `application.properties`**

Append the two lines to the end of `src/main/resources/application.properties`:

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://accounts.google.com
spring.security.oauth2.resourceserver.jwt.audiences=${GOOGLE_OAUTH_CLIENT_ID}
```

Do not touch `application-local.properties`. `GOOGLE_OAUTH_CLIENT_ID` must be provided by the deployment environment; Spring Boot resolves it from the environment.

- [ ] **Step 3: Create `SecurityConfig.java`**

Create `src/main/java/com/felixkroemer/smort/common/config/SecurityConfig.java` with exactly this content:

```java
package com.felixkroemer.smort.common.config;

import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String ALLOWED_EMAIL = "your-email@example.com";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
                .authorizeHttpRequests(
                        auth -> auth.anyRequest().access(allowedEmailAuthorizationManager()))
                .build();
    }

    @Bean
    AuthorizationManager<RequestAuthorizationContext> allowedEmailAuthorizationManager() {
        return (Supplier<Authentication> authentication, RequestAuthorizationContext context) -> {
            Jwt jwt =
                    authentication.get() instanceof JwtAuthenticationToken token
                            ? token.getToken()
                            : null;
            boolean allowed = jwt != null && ALLOWED_EMAIL.equals(jwt.getClaimAsString("email"));
            return new AuthorizationDecision(allowed);
        };
    }
}
```

Notes for the implementer:
- No `sessionManagement(...)` and no `.cors(...)` calls, per the spec.
- Unauthenticated requests produce `authentication.get()` without any `Jwt` -> denied -> 401. A valid token whose `email` claim differs from `ALLOWED_EMAIL` -> denied -> 403.
- A token without the `email` claim yields `null` from `getClaimAsString("email")` -> denied by the equals check.

- [ ] **Step 4: Verify placement by inspection**

Read the created files and confirm:
- `pom.xml` contains the resource-server dependency (Step 1).
- `application.properties` ends with the two JWT lines (Step 2).
- `SecurityConfig.java` imports and class body match Step 3 exactly, and the class sits in `common/config`.
- `SmortConfig.java` is unchanged.

Do NOT run the build. Copy this line into the final report: "Compilation skipped per AGENTS.md; the human verifies the build."

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/resources/application.properties \
  src/main/java/com/felixkroemer/smort/common/config/SecurityConfig.java
git commit -m "feat: add OAuth2 resource server auth (Google ID token)"
```

Commit to branch `feat/oauth2-resource-server` in the worktree only. Do not push or merge yet.