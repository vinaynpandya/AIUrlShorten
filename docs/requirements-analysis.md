# Requirements Analysis — URL Shortener

## 1. Functional Requirements

- Create a short URL from a valid long URL, with an optional custom alias.
- Redirect a short code to its original long URL via HTTP 302.
- Retrieve basic click analytics for a short code.
- Reject invalid, unknown, expired, or duplicate-alias requests with appropriate status codes.
- Support optional expiration on a short URL.

## 2. Non-Functional Requirements

- Correctness: no two active short codes may resolve to different targets; collisions must be detected and retried.
- Security: only syntactically valid HTTP/HTTPS URLs accepted; no open redirect beyond the stored target; no authentication in this prototype (documented limitation).
- Reliability: click-count updates must be atomic at the database level to avoid lost updates under concurrent redirects.
- Observability: expose health/metrics via Spring Boot Actuator; log creation, redirect, and error events.
- Maintainability: layered architecture (controller/service/repository/entity/DTO/exception/util); constructor injection; no entity leakage through APIs.
- Portability: H2 for the prototype; schema and access patterns must not preclude a future move to PostgreSQL/Redis.

## 3. Core Entities

**ShortUrl**
- `id` (PK)
- `shortCode` (unique, 7-char Base62, or custom alias)
- `originalUrl`
- `createdAt` (UTC)
- `expiresAt` (UTC, nullable)
- `clickCount` (default 0)
- `lastAccessedAt` (UTC, nullable)

No separate click-event entity in this prototype — analytics are aggregate fields on `ShortUrl`, consistent with the "basic analytics" scope decision.

## 4. Proposed APIs

| Method | Path | Purpose | Success | Failure |
|---|---|---|---|---|
| POST | `/api/v1/urls` | Create a short URL (optional custom alias, optional expiry) | 201 Created | 400 invalid URL, 409 alias taken |
| GET | `/{shortCode}` | Redirect to original URL | 302 Found | 404 unknown, 410 expired |
| GET | `/api/v1/urls/{shortCode}/analytics` | Return click analytics | 200 OK | 404 unknown |

Response URLs are built from a configurable `app.base-url` property rather than the request's `Host` header, avoiding Host-header injection and giving deployment-time control over the returned domain.

## 5. Ambiguities and Assumptions

- Custom alias format: assumed alphanumeric plus `-`/`_`, 3–20 characters, validated separately from generated Base62 codes.
- Short codes are case-sensitive (standard Base62 behavior).
- No default expiration; URLs are non-expiring unless `expiresAt` is supplied at creation.
- Analytics are limited to `clickCount` and `lastAccessedAt`; no per-click history, IP, geo, or referrer capture.
- URL validation is syntactic only (well-formed HTTP/HTTPS URI); no live reachability or SSRF-target checks.
- Timestamps are stored and returned in UTC.

## 6. Failure Scenarios

- Malformed or non-HTTP(S) URL submitted → 400.
- Requested custom alias already in use → 409.
- Base62 collision on generated code → transparent retry with a bounded attempt limit; exhaustion is a 500 (should not occur at prototype scale).
- Short code not found → 404.
- Short code found but past `expiresAt` → 410.
- Concurrent redirects to the same code → atomic DB-level increment (e.g., `UPDATE ... SET click_count = click_count + 1`) prevents lost updates.

## 7. Security and Reliability Risks

- No authentication/authorization: any client can create or query URLs. Documented as a prototype limitation, not a production posture.
- No rate limiting: the service is open to enumeration and abuse at volume. Listed as a production improvement, not implemented here.
- Open-redirect surface: the service inherently redirects to arbitrary attacker-supplied HTTP/HTTPS URLs; this is the product's function, but should be paired with abuse monitoring in production.
- Base-URL construction from configuration (not request headers) avoids Host-header-based response manipulation.
- Race conditions on click counting are mitigated by atomic DB updates rather than application-level read-modify-write.

## 8. Acceptance Criteria

- Valid URL submission returns 201 with a resolvable short code and full short URL built from `app.base-url`.
- Duplicate custom alias returns 409 and creates no record.
- Non-HTTP(S) or malformed URL returns 400 and creates no record.
- Redirect on a valid, active, non-expired code returns 302 with the correct `Location` header.
- Redirect on an unknown code returns 404; on an expired code returns 410.
- Analytics endpoint returns current `clickCount` and `lastAccessedAt`, and only after at least one redirect has occurred `lastAccessedAt` is non-null.
- Concurrent redirect requests against the same code do not lose click-count increments.

## 9. Prototype Scope

- Single-instance Spring Boot application with embedded H2.
- Synchronous request handling; no caching layer.
- No authentication, no rate limiting, no multi-tenant support.
- Aggregate analytics only, no historical click log.

## 10. Production-Scale Improvements

- Replace H2 with PostgreSQL for durability and concurrent-write correctness at scale.
- Introduce Redis (or similar) as a read-through cache in front of the redirect path to reduce database load.
- Add authentication/authorization (e.g., API keys or OAuth2) for URL creation and analytics access.
- Add rate limiting and abuse detection (e.g., per-IP or per-key quotas).
- Move click tracking to an async/event-based pipeline (e.g., queue + batch writer) if per-click history or high-volume analytics are required.
- Add horizontal scaling with a distributed unique-code generation strategy (e.g., pre-allocated ranges or a coordination service) to avoid collision-retry contention under load.
