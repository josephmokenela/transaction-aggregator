package io.mokenela.transactionaggregator.adapter.in.web;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import io.mokenela.transactionaggregator.domain.model.CustomerId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Issues signed JWTs for API access.
 *
 * <p>In a production deployment this would be replaced by an external identity provider
 * (e.g. Keycloak, Auth0). The token endpoint is intentionally self-contained so the service
 * can be run and evaluated standalone without any IdP dependency.
 *
 * <p>Tokens use HMAC-SHA256 signed with the secret configured at
 * {@code app.security.jwt.secret}. The secret is sourced from Vault in docker/k8s profiles.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Issue JWT tokens for API access")
class AuthController {

    // 10 token requests per minute per JVM instance — enough for legitimate use,
    // low enough to make brute-forcing the admin secret impractical.
    private static final RateLimiter rateLimiter = RateLimiter.of("auth",
            RateLimiterConfig.custom()
                    .limitForPeriod(10)
                    .limitRefreshPeriod(Duration.ofMinutes(1))
                    .timeoutDuration(Duration.ZERO)
                    .build());

    private final JwtTokenService jwtTokenService;
    private final String adminSecret;

    AuthController(JwtTokenService jwtTokenService,
                   @Value("${app.security.jwt.admin-secret}") String adminSecret) {
        this.jwtTokenService = jwtTokenService;
        this.adminSecret = adminSecret;
    }

    @PostMapping("/token")
    @Operation(
            summary = "Issue a customer JWT",
            description = """
                    Returns a signed JWT scoped to the given customerId (ROLE_CUSTOMER).
                    Use the token as `Authorization: Bearer <token>` on subsequent requests.
                    Customers can only access their own data — the token's subject claim enforces this.
                    Rate limited to 10 requests per minute.
                    """
    )
    Mono<TokenResponse> customerToken(@Valid @RequestBody CustomerTokenRequest request) {
        return Mono.fromCallable(() -> jwtTokenService.issueCustomerToken(CustomerId.of(request.customerId())))
                .transformDeferred(RateLimiterOperator.of(rateLimiter))
                .onErrorMap(io.github.resilience4j.ratelimiter.RequestNotPermitted.class,
                        ex -> new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                                "Too many token requests — try again shortly"));
    }

    @PostMapping("/admin-token")
    @Operation(
            summary = "Issue an admin JWT",
            description = """
                    Returns a signed JWT with ROLE_ADMIN (and ROLE_CUSTOMER).
                    Requires the correct admin secret. Admins have unrestricted access to all resources.
                    Rate limited to 10 requests per minute.
                    """
    )
    Mono<TokenResponse> adminToken(@Valid @RequestBody AdminTokenRequest request) {
        return Mono.fromCallable(() -> {
                    if (!adminSecret.equals(request.secret())) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin secret");
                    }
                    return jwtTokenService.issueAdminToken();
                })
                .transformDeferred(RateLimiterOperator.of(rateLimiter))
                .onErrorMap(io.github.resilience4j.ratelimiter.RequestNotPermitted.class,
                        ex -> new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                                "Too many token requests — try again shortly"));
    }

    record CustomerTokenRequest(
            @NotBlank(message = "customerId is required") String customerId
    ) {}

    record AdminTokenRequest(
            @NotBlank(message = "secret is required") String secret
    ) {}
}
