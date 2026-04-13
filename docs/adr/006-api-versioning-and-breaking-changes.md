# ADR-006: API Versioning and Breaking Change Policy

**Status:** Accepted  
**Date:** 2026-04-13

## Context

All HTTP endpoints in this service are prefixed `/api/v1/`. This is a deliberate design
choice that needs a documented policy behind it, because a version prefix without a
breaking-change definition is meaningless — it signals intent without creating any
actual commitment.

Services that consume this API (other microservices, BFFs, mobile clients) make
assumptions about response shapes, field names, status codes, and pagination behaviour.
When those assumptions break across a deployment, consumers fail silently (a missing field
returns `null`) or loudly (a removed field causes a null pointer) with no warning.

The questions this ADR answers:
- What counts as a breaking change?
- How is a breaking change communicated and delivered?
- When does `v1` become `v2`?
- What is the support lifetime of a version?

## Decision

### 1. URL path versioning

Version is encoded in the URL path: `/api/v1/`, `/api/v2/`. This is the approach already
in use. It is chosen over header versioning (`Accept: application/vnd.api+json;version=1`)
and query-parameter versioning (`?version=1`) because:

- It is visible in logs, gateway configs, and network traces without decoding headers
- It is trivially cacheable — CDNs and proxies key on URL by default
- It is unambiguous in API gateway routing rules

The trade-off is that URL versioning is more verbose and requires clients to hardcode the
version segment. This is acceptable — the version is stable for the lifetime of the
contract, not something clients need to negotiate at runtime.

### 2. What constitutes a breaking change

**Breaking (requires a new version or a migration plan):**

| Change | Why it breaks |
|--------|---------------|
| Removing a response field | Consumers reading the field get `null` or fail |
| Renaming a response field | Equivalent to remove + add |
| Changing a field's type | `String` → `Integer` breaks deserialisation |
| Changing a field from optional to required in a request | Existing clients sending no value now fail validation |
| Removing an endpoint | Clients get 404 |
| Changing an endpoint's HTTP method | Clients get 405 |
| Changing the meaning of a status code | Clients react to the wrong condition |
| Changing pagination behaviour in a way that skips or repeats records | Clients building paginated UIs show incorrect data |
| Removing or renaming an enum value in a request or response | Clients depending on the value fail |

**Non-breaking (can be deployed without a version bump):**

| Change | Why it is safe |
|--------|----------------|
| Adding a new optional response field | Consumers ignore unknown fields |
| Adding a new optional request parameter with a documented default | Existing calls without the parameter continue to work |
| Adding a new endpoint | Clients that don't call it are unaffected |
| Adding a new enum value to a response | Clients must handle unknown values gracefully — see constraint below |
| Tightening validation on existing fields (e.g. reducing max length) | Only safe if the new limit is above what clients actually send — verify before deploying |
| Improving error message text | As long as the status code and error `code` field don't change |

**Consumer obligation for non-breaking changes:**

Consuming services must tolerate unknown fields and unknown enum values in responses.
If a consumer throws on an unknown response field or enum value, that consumer has
made a breaking assumption — the problem is in the consumer, not this service.
This expectation must be documented in the consumer's onboarding guide.

### 3. How breaking changes are delivered

**Option A — In-place with a deprecation window (preferred for small changes):**

1. Deploy the new behaviour alongside the old, controlled by a request parameter or
   header (e.g. `X-API-Version: 2` before the URL version changes).
2. Notify all known consumers with a minimum 4-week notice.
3. After the window, remove the old behaviour.

Use this for targeted field renames or semantic changes where a full version bump is
disproportionate.

**Option B — New URL version (`/api/v2/`):**

1. Implement `v2` endpoints in parallel with `v1`. Both run in the same service binary —
   no separate deployment.
2. Notify all known consumers. Provide a migration guide (diff of request/response shapes,
   code examples).
3. Support `v1` for a minimum of **6 months** after `v2` goes live, or until all known
   consumers have migrated — whichever is later.
4. Announce the `v1` sunset date at least 8 weeks in advance.
5. After the sunset date, `v1` endpoints return `410 Gone` with a body pointing to the
   `v2` migration guide.

**In both cases:** the OpenAPI specification is updated first, before implementation, so
consumers can validate against the new contract before deploying.

### 4. Version support lifetime

| Version state | Behaviour |
|---------------|-----------|
| Current (`v1`) | Fully supported, all bugs fixed |
| Deprecated | Supported but no new features; sunset date announced |
| Sunset | Returns `410 Gone`; removed in the next major release |

Only one version is "current" at any time. A second version may be in "deprecated" state
simultaneously, but maintaining more than two active versions is a support burden that
requires explicit justification.

### 5. The `X-Request-ID` header is a stable cross-service contract

The `X-Request-ID` correlation header is propagated by this service and must not be
renamed or removed without coordinating with every service in the call chain that
reads it for log correlation. Changing the header name is treated as a breaking change
even though it is not part of the response body schema.

If the organisation standardises on a different header name (e.g. `X-Correlation-ID` or
the W3C `traceparent`), the migration follows the same dual-header phase described in
ADR-005: emit both headers for a transition period, then remove the old one.

### 6. Pagination contract

The `PagedResponse<T>` structure (`content`, `page`, `size`, `totalElements`,
`totalPages`) is a shared contract across all paginated endpoints. Changes to this
structure are treated as breaking changes affecting all paginated consumers simultaneously.
Any change to pagination semantics requires a `v2` version bump, not a patch.

The maximum page size (`size=100`) and default page size (`size=20`) are part of the
contract. Reducing the maximum is a breaking change; increasing it is non-breaking.

## Consequences

- All new endpoints must be added under `/api/v1/` until a breaking change forces a
  version bump. There is no `/api/v0/` or unversioned path.
- The OpenAPI spec at `/v3/api-docs` and Swagger UI at `/swagger-ui.html` are the
  authoritative reference for the current version. They must be kept in sync with the
  implementation — stale OpenAPI specs are treated as bugs.
- Consumer teams that have not registered with the owning team may not receive
  deprecation notices. The sunset period (6 months minimum) is the safety net for
  unregistered consumers, but registration is strongly encouraged.
- The `v1` endpoints will remain the "current" version until a concrete need for `v2`
  arises. Creating `v2` preemptively without a real breaking change is prohibited —
  version inflation (v3, v4 within a year) signals poor change management, not maturity.

## Alternatives considered

**Header versioning (`Accept: application/vnd.api+json;version=1`):** Content negotiation
is more REST-pure but adds complexity to every API gateway rule, every log parser, and
every curl command. Rejected in favour of the simpler URL approach.

**No versioning, breaking changes coordinated via deployment windows:** Works when all
consumers are internal and deploy on the same cadence. Breaks down as soon as a mobile
client or external partner is involved. Rejected because it requires full deployment
coordination for every change, eliminating the value of service independence.

**Semantic versioning in URL (`/api/v1.2/`):** Minor versions in the path are ambiguous —
consumers don't know whether to track major or minor versions. Only the major version
appears in the URL. Minor and patch versions are visible in the OpenAPI spec's `info.version`
field and in the `X-API-Version` response header.
