package io.mokenela.transactionaggregator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.mokenela.transactionaggregator.adapter.in.web.ErrorResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final List<String> WEAK_SECRET_MARKERS = List.of("local-dev-only", "local-dev-admin", "change-me");

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    @Value("${app.security.jwt.admin-secret}")
    private String adminSecret;

    @Value("${spring.profiles.active:default}")
    private String activeProfiles;

    @Value("${app.security.cors.allowed-origins:http://localhost:3000,http://localhost:4200}")
    private List<String> allowedOrigins;

    /**
     * Fails fast if weak/default JWT secrets are detected in non-development profiles.
     * Allows easy local development while preventing accidental production deployment with insecure defaults.
     */
    @PostConstruct
    void validateSecrets() {
        boolean isDevProfile = activeProfiles.contains("default") || activeProfiles.contains("dev");
        if (isDevProfile) {
            if (WEAK_SECRET_MARKERS.stream().anyMatch(jwtSecret::contains)) {
                log.warn("Running with default JWT secret in dev profile — do NOT use in production");
            }
            return;
        }

        // Fail fast in docker / prod / staging profiles
        if (WEAK_SECRET_MARKERS.stream().anyMatch(jwtSecret::contains)) {
            throw new IllegalStateException(
                    "SECURITY: Weak JWT secret detected in profile '" + activeProfiles + "'. " +
                    "Set JWT_SECRET to a strong random value (openssl rand -base64 32).");
        }
        if (WEAK_SECRET_MARKERS.stream().anyMatch(adminSecret::contains)) {
            throw new IllegalStateException(
                    "SECURITY: Weak JWT admin secret detected in profile '" + activeProfiles + "'. " +
                    "Set JWT_ADMIN_SECRET to a strong random value.");
        }
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "JWT secret must be at least 32 characters (256 bits). Current length: " + jwtSecret.length());
        }

        log.info("JWT secrets validated for profile '{}'", activeProfiles);
    }

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                   ReactiveJwtDecoder jwtDecoder,
                                                   ReactiveJwtAuthenticationConverter jwtAuthConverter,
                                                   ObjectMapper objectMapper) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // Auth and public docs — no token required
                        .pathMatchers("/api/v1/auth/**").permitAll()
                        .pathMatchers("/actuator/**").permitAll()
                        .pathMatchers("/swagger-ui/**", "/swagger-ui.html",
                                "/api-docs/**", "/webjars/**").permitAll()
                        // Admin-only endpoints
                        .pathMatchers(HttpMethod.GET, "/api/v1/customers").hasRole("ADMIN")
                        .pathMatchers("/api/v1/sync/**").hasRole("ADMIN")
                        .pathMatchers("/api/v1/kafka/**").hasRole("ADMIN")
                        // Everything else requires a valid token
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthConverter)
                        )
                        .authenticationEntryPoint((exchange, ex) ->
                                writeError(exchange, objectMapper, HttpStatus.UNAUTHORIZED,
                                        "UNAUTHORIZED", "Authentication required"))
                        .accessDeniedHandler((exchange, ex) ->
                                writeError(exchange, objectMapper, HttpStatus.FORBIDDEN,
                                        "FORBIDDEN", "Access denied"))
                )
                .build();
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder() {
        return NimbusReactiveJwtDecoder.withSecretKey(secretKey()).build();
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

    @Bean
    ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new ReactiveJwtAuthenticationConverter();
        // Map the "roles" claim (e.g. ["ROLE_CUSTOMER"]) to Spring GrantedAuthority objects
        converter.setJwtGrantedAuthoritiesConverter(jwt ->
                Flux.fromIterable(
                        Optional.ofNullable(jwt.getClaimAsStringList("roles")).orElse(List.of())
                ).map(SimpleGrantedAuthority::new)
        );
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setMaxAge(3600L);  // cache preflight for 1 hour
        config.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private SecretKeySpec secretKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be at least 32 characters (256 bits) for HMAC-SHA256");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    private Mono<Void> writeError(ServerWebExchange exchange, ObjectMapper objectMapper,
                                   HttpStatus status, String code, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(ErrorResponse.of(code, message));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        } catch (Exception e) {
            return response.setComplete();
        }
    }
}
