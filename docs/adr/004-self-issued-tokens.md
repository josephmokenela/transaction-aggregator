# ADR-004: Self-Issued Tokens vs External Identity Provider

**Status:** Accepted (with known evolution path)  
**Date:** 2026-04-12

## Context

The service needs to authenticate API callers. The options are:

1. **Self-issued tokens:** The service has its own `/api/v1/auth/token` endpoint that issues signed JWTs
2. **External IdP:** Delegate authentication to Keycloak, Auth0, Okta, etc. The service only validates tokens — it never issues them

## Decision

Implement self-issued tokens for the initial version. The `AuthController` is explicitly documented as a placeholder to be replaced by an external IdP in a production deployment.

## Rationale

**Why self-issued for now:**
- The service runs standalone with `docker compose up` or `./mvnw spring-boot:run` — no external dependency required to evaluate or demo it
- The JWT issuance code (`JwtTokenService`, `SecurityConfig`) is less than 100 lines and demonstrates the correct concepts without IdP infrastructure overhead
- The `SecurityConfig` is already written as an OAuth2 resource server — switching to an external IdP requires only changing the `jwtDecoder` bean to point at the IdP's JWKS URI, not a structural rewrite

**Why an external IdP in production:**
- Self-issued tokens with a single secret have no user management, no password reset, no MFA, no session invalidation
- Token revocation requires a shared blacklist (Redis/DB) — self-issued JWTs are stateless and cannot be invalidated before expiry without additional infrastructure
- An IdP (Keycloak, Auth0) brings all of this out of the box with years of security hardening

## Migration path

To migrate to Keycloak:

1. Deploy Keycloak, create a realm and client
2. Replace the `jwtDecoder` bean in `SecurityConfig`:
   ```java
   @Bean
   ReactiveJwtDecoder jwtDecoder() {
       return ReactiveJwtDecoders.fromIssuerLocation(
           "https://keycloak.example.com/realms/my-realm"
       );
   }
   ```
3. Map Keycloak roles to Spring `GrantedAuthority` via the existing `jwtAuthenticationConverter` bean — adjust the claim name if needed (`realm_access.roles` vs the current `roles` claim)
4. Remove `AuthController`, `JwtTokenService`, and the `JwtEncoder` bean
5. Remove `app.security.jwt.secret` and `app.security.jwt.admin-secret` config

The `SecurityConfig` path rules and all controller-level ownership checks (`JwtUtils`) remain unchanged.

## Consequences

- The current `POST /api/v1/auth/token` endpoint accepts any UUID as a `customerId` without verifying the customer exists — this is intentional for demo purposes but must not be the production auth flow
- Rate limiting (10 req/min) on the token endpoint mitigates brute-forcing but does not replace proper IdP-level protections
