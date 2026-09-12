# Design: OAuth2 Resource Server Authentication (Google ID Token)

Date: 2026-09-12

## Goal

Add authentication to the smort API so only its owner can access it. The backend
becomes a pure OAuth2 resource server: it validates Google ID tokens presented as
Bearer JWTs and authorizes a single hardcoded email address. A frontend does
Google Sign-In and sends the returned ID token on each request.

## Context

- Spring Boot 4.0.3, Java 25. No Spring Security on the classpath today.
- REST controllers: Analysis, Deck, Note, UserSettings, Cron.
- Single-user application: all data is keyed on a hardcoded `"default"` user id.
- No frontend is served by this application yet; the frontend will run on the
  same origin (browser CORS rules do not apply, no security CORS config needed).
- A CORS bean exists in `SmortConfig`, active only on the `local` profile. It
  stays untouched.
- springdoc swagger-ui is present and will be protected like any other endpoint.

## Approach

### Dependency

Add to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

This transitively brings in `spring-boot-starter-security`.

### Configuration properties

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://accounts.google.com
spring.security.oauth2.resourceserver.jwt.audiences=${GOOGLE_OAUTH_CLIENT_ID}
```

- `issuer-uri` triggers OIDC discovery: Spring Boot auto-configures the
  `JwtDecoder` bean with the default validators (signature, expiry, issuer).
- `audiences` wires the audience validator, requiring the ID token's `aud` to
  match the Google OAuth client ID used by the frontend. The client ID is a
  public value and comes from the environment.
- No custom `JwtDecoder`, no custom `OAuth2TokenValidator` beans. A token without
  the `email` claim is denied by the authorization layer anyway, and an
  `email_verified` check was considered and dropped as unnecessary for a single
  hardcoded email owned by the app owner.

### SecurityConfig

New class in `com.felixkroemer.smort.common.config`:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private static final String ALLOWED_EMAIL = "your-email@example.com"; // TODO: replace

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) {
    return http
        .csrf(csrf -> csrf.disable())
        .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
        .authorizeHttpRequests(
            auth -> auth.anyRequest().access(allowedEmailAuthorizationManager()))
        .build();
  }

  @Bean
  AuthorizationManager<RequestAuthorizationContext> allowedEmailAuthorizationManager() {
    return (auth, context) -> {
      Jwt jwt = auth.get() instanceof JwtAuthenticationToken token ? token.getToken() : null;
      boolean allowed = jwt != null && ALLOWED_EMAIL.equals(jwt.getClaimAsString("email"));
      return new AuthorizationDecision(allowed);
    };
  }
}
```

- The `ALLOWED_EMAIL` constant is a placeholder the owner replaces with their
  Google account email.
- CSRF is disabled: it only protects cookie-based sessions and a stateless
  bearer-token API has none.
- No explicit session config: Spring Boot's default (`IF_REQUIRED`) is kept; a
  pure bearer-token resource server never creates a session.
- A single `anyRequest()` rule applies the email check to every endpoint,
  including swagger and the cron endpoints. No `@PreAuthorize` annotations and
  no per-method config anywhere.
- Unauthenticated requests (no token, invalid token) are denied -> 401. Tokens
  with a different email are denied -> 403.

### Out of scope (deliberately)

- No `sessionManagement(...STATELESS)` line: default Boot behavior.
- No `cors()` / `CorsConfigurationSource` in security: frontend is same-origin.
- No changes to the existing `SmortConfig` CORS bean.
- No custom `AuthenticationEntryPoint` / `AccessDeniedHandler`: spring defaults
  return empty 401 / 403 bodies.
- No multi-user support, no user registry, no token refresh handling (frontend
  renews tokens), no public swagger.

## Error handling

Spring Security defaults apply: missing/invalid token -> 401 (empty body), valid
token with a non-matching email -> 403 (empty body). No custom error bodies in
this iteration.

## Testing

Per AGENTS.md, tests are only written when explicitly requested. No tests are
included in this plan. If authorization tests are requested later, the tool is
`@WithMockJwt` from `spring-security-test`.