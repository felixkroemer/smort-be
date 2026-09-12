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
