package dev.fhirvault.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    /** Discovery endpoints a SMART client needs before it has a token. */
    static final String[] PUBLIC_PATHS = {"/fhir/metadata", "/fhir/.well-known/smart-configuration"};

    /** Open access for local development and tests. Must be enabled explicitly. */
    @Bean
    @Profile("noauth")
    public SecurityFilterChain openFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    /**
     * Default chain: every FHIR call needs an unexpired JWT from the configured realm, issued for this
     * API. The SMART scopes then decide per request what it can reach, in {@link
     * SmartAuthorizationInterceptor} and {@link SmartAuthorizer}.
     */
    @Bean
    @Profile("!noauth")
    public SecurityFilterChain jwtFilterChain(HttpSecurity http) throws Exception {
        return http
                // Stateless bearer-token API with no cookies, so CSRF doesn't apply.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(new OperationOutcomeAuthenticationEntryPoint()))
                .exceptionHandling(e -> e.authenticationEntryPoint(new OperationOutcomeAuthenticationEntryPoint()))
                .build();
    }

    /** Allowed origins for browser-based SMART apps come from configuration. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${fhirvault.cors.allowed-origins:}") String allowedOrigins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "If-Match", "Prefer"));
        cors.setExposedHeaders(List.of("Location", "Content-Location", "ETag", "Last-Modified"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/fhir/**", cors);
        return source;
    }
}
