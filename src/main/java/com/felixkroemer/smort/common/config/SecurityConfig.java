package com.felixkroemer.smort.common.config;

import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final String allowedEmail;

  public SecurityConfig(@Value("${smort.auth.allowed-email}") String allowedEmail) {
    this.allowedEmail = allowedEmail;
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
        .authorizeHttpRequests(auth -> auth.anyRequest().access(allowedEmailAuthorizationManager()))
        .build();
  }

  @Bean
  AuthorizationManager<RequestAuthorizationContext> allowedEmailAuthorizationManager() {
    return (Supplier<? extends Authentication> authentication, RequestAuthorizationContext _) -> {
      Jwt jwt =
          authentication.get() instanceof JwtAuthenticationToken token ? token.getToken() : null;
      boolean allowed = jwt != null && allowedEmail.equals(jwt.getClaimAsString("email"));
      return new AuthorizationDecision(allowed);
    };
  }
}
