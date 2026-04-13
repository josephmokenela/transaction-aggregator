# ADR-003: JWT Signing Strategy (HS256 over RS256)

**Status:** Accepted  
**Date:** 2026-04-12

## Context

The service issues and validates its own JWTs. A signing key is required. The two mainstream options are:

- **HS256 (HMAC-SHA256):** Symmetric — the same secret is used to sign and verify
- **RS256 (RSA-SHA256):** Asymmetric — a private key signs, a public key verifies

## Decision

Use HS256 with a secret sourced from Vault (or `JWT_SECRET` env var). The secret must be at least 32 bytes (256 bits) — enforced at startup.

## Rationale

HS256 is appropriate here because:

1. **Single service, single issuer:** Both token issuance and validation happen inside the same service. There is no scenario where an external party needs to validate tokens without also having access to the signing secret.

2. **Operational simplicity:** RS256 requires managing a key pair — generating keys, rotating them, exposing a JWKS endpoint, handling key ID (`kid`) header matching. For a standalone service with Vault managing secrets, this complexity is not justified.

3. **Vault already solves the secret distribution problem:** The primary argument for RS256 is that the public key can be shared without exposing the signing capability. Vault's secret engine provides secret distribution without public key infrastructure.

## When to revisit

Switch to RS256 (or use an external IdP) when:
- Multiple services need to validate tokens without contacting this service
- A public JWKS endpoint is required for third-party integrations
- Key rotation must happen transparently without a service restart

## Consequences

- The JWT secret must be treated as a credential — compromised means all tokens can be forged until the secret is rotated and all outstanding tokens expire
- Secret rotation requires a coordinated update: update Vault, restart the service, existing tokens issued with the old secret will fail validation immediately
- At least 32 bytes enforced at startup (`SecurityConfig.secretKey()`) — too-short secrets are a startup failure, not a runtime warning
