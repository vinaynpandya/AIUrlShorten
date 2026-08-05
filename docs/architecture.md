# Architecture — URL Shortener

```mermaid
flowchart LR
    Client["Browser / API Client"] --> Nginx["NGINX Load Balancer"]
    Nginx --> App1["Spring Boot app1"]
    Nginx --> App2["Spring Boot app2"]
    App1 --> Redis[("Redis Cache")]
    App2 --> Redis
    App1 --> Postgres[("PostgreSQL")]
    App2 --> Postgres
```

The diagram above is the implemented `docker-compose.yml` topology (`scalable`
Spring profile): NGINX load-balances across two stateless application
instances that share a single Redis cache and a single PostgreSQL database,
so either instance can serve any request. Section 14 describes this
deployment in detail; Section 1 below describes the single-instance
prototype that each `app1`/`app2` container runs internally.

## 1. Prototype Architecture Overview

The prototype is a single-instance Spring Boot application exposing a
REST API for URL shortening, redirection, and basic analytics. It
follows a conventional layered architecture (controller → service →
repository → database) with DTOs at the API boundary so JPA entities
are never exposed directly to clients. Requests are handled
synchronously against an embedded H2 database; there is no caching
layer, no authentication, and no horizontal scaling in this phase.

This design intentionally favors correctness and clarity over scale.
Every production concern deferred here (caching, async analytics,
distributed code generation) is documented in Section 12 rather than
built prematurely, per the project's "avoid unnecessary abstractions"
rule.

## 2. Components and Responsibilities

| Component | Responsibility |
|---|---|
| `UrlController` | Handles `POST /api/v1/urls` (create) and `GET /api/v1/urls/{shortCode}/analytics`. Delegates all logic to the service layer; maps DTOs in/out. |
| `RedirectController` | Handles `GET /{shortCode}`. Resolves the code, applies expiry rules, issues the `302` redirect. |
| `ShortUrlService` | Core business logic: input validation orchestration, alias vs. generated-code decision, collision-retry loop, atomic click-count update, expiry check. |
| `ShortUrlRepository` | Spring Data JPA repository over `ShortUrl`. Includes a `@Modifying` atomic increment query for click counting. |
| `ShortUrl` (entity) | JPA entity mapping to the single database table. Never returned from a controller. |
| Request/Response DTOs | `CreateUrlRequest`, `CreateUrlResponse`, `AnalyticsResponse`. The only shapes the API contract exposes. |
| `ShortCodeGenerator` (util) | Generates 7-character Base62 codes; stateless, no persistence knowledge. |
| `GlobalExceptionHandler` | `@RestControllerAdvice` translating domain exceptions (not-found, expired, alias-conflict, validation) into the correct HTTP status codes and a consistent error body. |
| Custom exceptions | `ShortUrlNotFoundException`, `ShortUrlExpiredException`, `AliasAlreadyExistsException`, `InvalidUrlException` — carry intent from service to handler without leaking persistence details. |

## 3. Tools and Technology Choices

| Choice | Reason |
|---|---|
| Java 17 / Spring Boot | Mandated by project instructions; mature ecosystem for layered REST services. |
| Spring Web | REST controllers and MVC dispatch. |
| Spring Data JPA | Repository abstraction; supports the atomic `@Modifying` update needed for safe click counting without hand-written JDBC. |
| Jakarta Validation | Declarative request validation (`@NotBlank`, `@Pattern`, `@Valid`) at the DTO boundary — keeps validation out of controllers/services. |
| H2 (embedded) | Zero-setup runnable prototype; schema kept portable so a move to PostgreSQL requires no structural rework (see Section 12). |
| Spring Boot Actuator | Health and metrics endpoints for observability, as required by the non-functional requirements. |
| Lombok | Reduces entity/DTO boilerplate (getters/setters/constructors); already present in `pom.xml`, no new dependency introduced. |
| JUnit 5 / Mockito / MockMvc | Unit tests for service logic (Mockito) and slice tests for controllers (MockMvc), per the required test coverage. |

No dependency beyond what `pom.xml` already declares is required for this architecture.

## 4. URL Creation Control Flow

1. Client sends `POST /api/v1/urls` with `originalUrl` and optional `customAlias` / `expiresAt`.
2. `UrlController` validates the request shape via `@Valid` (Jakarta Validation) and delegates to `ShortUrlService`.
3. `ShortUrlService` validates `originalUrl` is a syntactically well-formed HTTP/HTTPS URI.
4. If `customAlias` is present: check uniqueness via the repository; if taken, throw `AliasAlreadyExistsException` → `409`.
5. If no alias: generate a Base62 code via `ShortCodeGenerator`; attempt to persist; on a unique-constraint violation, retry generation up to a bounded attempt limit.
6. Persist the `ShortUrl` entity (`createdAt` set server-side, `clickCount = 0`).
7. Map to `CreateUrlResponse`, building the returned short URL from the configurable `app.base-url` property (not the request `Host` header).
8. Return `201 Created`.

## 5. Redirect and Analytics Control Flow

**Redirect (`GET /{shortCode}`):**
1. `RedirectController` calls `ShortUrlService.resolve(shortCode)`.
2. Service looks up the entity; if absent, throw `ShortUrlNotFoundException` → `404`.
3. If `expiresAt` is set and in the past, throw `ShortUrlExpiredException` → `410`.
4. On success, issue an atomic DB-level increment of `clickCount` and update `lastAccessedAt` (single `UPDATE ... SET click_count = click_count + 1, last_accessed_at = ?` statement — no read-modify-write in application code).
5. Controller returns `302 Found` with `Location: originalUrl`.

**Analytics (`GET /api/v1/urls/{shortCode}/analytics`):**
1. Service looks up the entity; `404` if absent.
2. Maps `clickCount` and `lastAccessedAt` to `AnalyticsResponse`.
3. Returns `200 OK`. (Analytics lookups do not themselves count as clicks.)

## 6. Database Model

Single table, matching `requirements-analysis.md` §3:

| Column | Type | Constraints |
|---|---|---|
| `id` | BIGINT | PK, generated |
| `short_code` | VARCHAR | UNIQUE, NOT NULL |
| `original_url` | VARCHAR | NOT NULL |
| `created_at` | TIMESTAMP (UTC) | NOT NULL |
| `expires_at` | TIMESTAMP (UTC) | nullable |
| `click_count` | BIGINT | NOT NULL, default 0 |
| `last_accessed_at` | TIMESTAMP (UTC) | nullable |

No separate click-event table in the prototype — analytics remain aggregate fields, per the documented scope decision. The unique index on `short_code` is the sole enforcement mechanism for collision/alias-conflict detection at the database level.

## 7. API Summary

| Method | Path | Success | Failure |
|---|---|---|---|
| `POST` | `/api/v1/urls` | `201 Created` | `400` invalid URL, `409` alias taken |
| `GET` | `/{shortCode}` | `302 Found` | `404` unknown, `410` expired |
| `GET` | `/api/v1/urls/{shortCode}/analytics` | `200 OK` | `404` unknown |

## 8. Short-Code Generation and Collision Handling

- Generated codes are 7-character Base62 (`[A-Za-z0-9]`), giving a search space large enough that collisions are rare at prototype scale but not impossible.
- Custom aliases follow a separate, stricter format (alphanumeric plus `-`/`_`, 3–20 characters) and are validated independently of generated codes.
- On insert, a collision (generated code) is detected via the database unique constraint, not a pre-check-then-insert pattern — this avoids a TOCTOU race between two concurrent creations.
- The service retries generation up to a bounded attempt count on collision; exhaustion surfaces as `500`, which is expected to be effectively unreachable at prototype data volumes.
- Custom-alias conflicts are a distinct code path (`409`, no retry — the client owns the alias choice).

## 9. Atomic Analytics-Update Approach

Click counting is the primary concurrency risk in this system: many redirects can hit the same `shortCode` simultaneously. The prototype avoids application-level read-modify-write (`read count → increment in Java → write count`), which loses updates under concurrent access, in favor of a single atomic SQL statement issued through a `@Modifying` repository method:

```sql
UPDATE short_url
SET click_count = click_count + 1, last_accessed_at = :now
WHERE short_code = :shortCode
```

This pushes the increment into the database's own atomic write path, so correctness does not depend on application-level locking, and no lost updates occur regardless of concurrent redirect volume.

## 10. Validation and Exception Handling

- **Input validation**: Jakarta Validation annotations on DTOs (`@NotBlank`, `@Pattern` for alias format, `@URL`-equivalent check for `originalUrl`) enforced via `@Valid` at the controller boundary. Validation failures are handled by the global handler and return `400` with field-level detail.
- **Domain exceptions**: `ShortUrlNotFoundException` (404), `ShortUrlExpiredException` (410), `AliasAlreadyExistsException` (409), `InvalidUrlException` (400) — thrown from the service layer, never constructed in controllers.
- **Global handler**: a single `@RestControllerAdvice` maps each domain exception and `MethodArgumentNotValidException` to its HTTP status and a consistent JSON error body (`{"status", "message", "timestamp"}`), keeping controllers free of try/catch blocks.
- **Security note**: the redirect target is always the value stored at creation time — the service never redirects to a URL derived from request parameters other than the resolved `shortCode` lookup, limiting the API's open-redirect surface to its intended function.

## 11. Key Architecture Decisions with Rationale

| Decision | Rationale |
|---|---|
| Aggregate analytics fields on `ShortUrl`, no click-event table | Matches "basic analytics" scope; avoids an unnecessary table/write-amplification for a prototype. |
| Atomic `UPDATE` for click counting instead of `@Version`/optimistic locking | A single-statement increment has no retry/conflict-exception path for the caller to handle, unlike optimistic locking, and matches the "no lost updates" requirement directly. |
| Base URL from `app.base-url` config, not the `Host` header | Prevents Host-header injection into returned short URLs; gives deployment-time control. |
| Unique DB constraint as the collision source of truth (not a pre-check) | Pre-check-then-insert is race-prone under concurrent creation; the constraint plus bounded retry is race-safe by construction. |
| DTOs at every API boundary, entity never serialized | Required by project architecture rules; also decouples the wire contract from persistence schema evolution. |
| Synchronous, single-instance, no cache | Correct match for prototype scope; premature caching/async would add complexity the requirements don't yet justify. |

## 12. Prototype Architecture vs. Production-Scale Architecture

| Concern | Prototype (default profile) | Production | Status |
|---|---|---|---|
| Database | Embedded H2 | PostgreSQL — durable storage, proven concurrent-write correctness | **Implemented** — `scalable` profile (Section 14) |
| Read path | Direct DB read per redirect | Redis cache-aside in front of the redirect path to reduce DB load | **Implemented** — `scalable` profile (Section 14) |
| Deployment | Single instance | Load-balanced multi-instance behind a reverse proxy/load balancer | **Implemented** — NGINX + `app1`/`app2` (Section 14) |
| AuthN/AuthZ | None (documented limitation) | API keys or OAuth2 for creation and analytics access | Not implemented |
| Abuse protection | None | Rate limiting / quotas (per-IP or per-key) | Not implemented |
| Click tracking | Synchronous atomic `UPDATE` per redirect | Async/event pipeline (queue + batch writer) if per-click history or high-volume analytics are needed | Not implemented |
| Code generation at scale | Single-instance bounded retry | Distributed unique-code strategy (pre-allocated ranges or coordination service) to avoid collision-retry contention across instances | Not implemented |
| Observability | Actuator health/metrics, logs | Actuator + centralized metrics (e.g., Micrometer → Prometheus/Grafana), structured logging, tracing | Actuator only; no centralized metrics/tracing |

## 13. Component and Flow Diagram

```mermaid
flowchart TD
    Client[Client]

    subgraph API["Spring Boot Application"]
        UC[UrlController]
        RC[RedirectController]
        SVC[ShortUrlService]
        GEN[ShortCodeGenerator]
        REPO[ShortUrlRepository]
        GEH[GlobalExceptionHandler]
    end

    DB[(H2 Database<br/>short_url table)]

    Client -->|POST /api/v1/urls| UC
    Client -->|GET /shortCode| RC
    Client -->|GET /api/v1/urls/shortCode/analytics| UC

    UC --> SVC
    RC --> SVC
    SVC --> GEN
    SVC --> REPO
    REPO --> DB

    SVC -.throws domain exception.-> GEH
    GEH -.400/404/409/410.-> Client

    UC -->|201 Created| Client
    RC -->|302 Found| Client
    UC -->|200 OK analytics| Client
```
## Short-Code Capacity and Uniqueness

The prototype generates seven-character Base62 codes using the characters
0-9, a-z and A-Z.

A seven-character Base62 namespace contains:

62^7 = approximately 3.52 trillion combinations.

A large namespace reduces collision probability but does not guarantee
uniqueness. The prototype therefore uses three safeguards:

1. Check whether the generated code already exists.
2. Retry generation up to five times.
3. Enforce a unique database constraint as the final correctness guarantee.

The repository existence check is an optimization. The database constraint
is still required because concurrent requests could generate the same code
between the existence check and persistence operation.

Short codes are never recycled after expiration or deactivation. Reusing
codes could cause old bookmarks, delayed requests, browser caches or
historical analytics to refer to a different destination.

## Redirect Decision

The service returns HTTP 302 rather than HTTP 301.

HTTP 302 was selected because:

- Browsers are less likely to cache the redirect permanently.
- Expiration remains enforceable.
- Redirect requests continue reaching the service for analytics.
- Future destination changes remain possible.

## Analytics Trade-Off

The prototype updates click analytics synchronously and atomically during
the redirect request.

Advantages:

- Simple implementation.
- Immediately consistent click counts.
- Easy end-to-end validation.

Limitations:

- Analytics writes add latency to the redirect request.
- Database write load increases with redirect traffic.
- Analytics availability can affect redirect performance.

In a production-scale system, the redirect service would publish a click
event asynchronously to Kafka or another message broker. Analytics
processing would then occur independently of redirect handling.

## Prototype Versus Production Architecture

The runnable prototype uses:

- One modular Spring Boot application.
- H2 database.
- SecureRandom Base62 code generation.
- Database uniqueness enforcement.
- Synchronous click analytics.
- Spring Boot Actuator health monitoring.

A production implementation could evolve to:

- PostgreSQL for durable relational persistence. **Implemented** — see Section 14.
- Redis cache for frequently accessed redirect mappings. **Implemented** — see Section 14.
- Multiple application instances behind a load balancer. **Implemented** — see Section 14.
- Rate limiting for URL creation and redirect endpoints.
- Asynchronous analytics through Kafka.
- Centralized logs, metrics and distributed tracing.
- Read replicas or partitioned storage.
- CDN or edge-based redirects for global latency reduction.

At extremely large scale, random generation with database collision checks
would be replaced by guaranteed ID allocation. Application instances could
receive non-overlapping numeric ranges, Base62-encode those IDs and apply a
reversible permutation to make public codes less predictable.

Distributed ID allocation, Cassandra, DynamoDB, ZooKeeper, Kafka, sharding
and CDN infrastructure remain documented as further production evolution
and are intentionally not implemented. Redis, PostgreSQL, and multi-instance
load balancing are implemented via the `scalable` Docker Compose profile
(Section 14).

## 14. Scalable Deployment (Docker Compose `scalable` Profile)

The diagram at the top of this document reflects `docker-compose.yml`:

| Component | Role |
|---|---|
| `nginx` | NGINX reverse proxy on port `8080`, load-balancing across `app1` and `app2` with `least_conn` and passive health checks (`max_fails=3 fail_timeout=10s`); forwards `Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto`. |
| `app1`, `app2` | Two instances of the same Spring Boot image, run under the `scalable` profile. Stateless — neither holds data the other doesn't also see, since both connect to the same `redis` and `postgres` containers. |
| `redis` | Single shared Redis instance used as a cache-aside layer in front of the redirect path only (not creation or analytics). |
| `postgres` | Single shared PostgreSQL instance — the source of truth for all reads/writes. |

**Cache-aside redirect flow** (`UrlShortenerService.resolveOriginalUrl`,
`app.cache.enabled=true` under the `scalable` profile via
`RedisRedirectCache`):
1. Look up the short code in Redis (`redirect:<shortCode>`). On a hit, issue
   a conditional atomic `UPDATE` in PostgreSQL that only succeeds if the row
   is still active and unexpired; if it succeeds, return the cached URL
   without a read of the URL column. If it affects zero rows (the row
   became inactive/expired since caching), evict the stale cache entry and
   fall through to step 2.
2. On a cache miss (or the fallthrough above), read from PostgreSQL,
   applying the same active/expiry checks as the prototype path
   (`404`/`410`).
3. Increment the click count in PostgreSQL, populate the Redis entry with a
   TTL of `min(app.cache.redirect-ttl, expiresAt)`, and return the URL.

All Redis operations are wrapped to catch `DataAccessException`: a Redis
outage degrades to direct PostgreSQL reads rather than failing the request.
When `app.cache.enabled=false` (the prototype's default profile),
`NoOpRedirectCache` is used instead and every request goes straight to
PostgreSQL — this is how the same codebase runs identically in both the
single-instance H2 prototype and the multi-instance PostgreSQL/Redis
deployment.

**Instance identification**: `InstanceHeaderFilter` sets an
`X-App-Instance` response header from `app.instance-name`
(`INSTANCE_NAME=app1`/`app2` in `docker-compose.yml`), letting a client
verify NGINX is actually distributing requests across both instances.

**Configuration**: `application-scalable.properties` sources
`DB_URL`/`DB_USERNAME`/`DB_PASSWORD` and `REDIS_HOST`/`REDIS_PORT` from
environment variables supplied by `docker-compose.yml` (in turn sourced
from `.env`, using `.env.example` as the non-production placeholder
template) — no credentials are hardcoded in the properties file or image.