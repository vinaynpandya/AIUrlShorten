# Scenarios, Validation, and Risks

## 1. Purpose

This document maps the required engineering scenarios to the actual code,
tests, execution evidence, risks, and trade-offs.

## 2. Scenario 1: Greenfield core URL shortener

### 2.1 Requirement

The system must create a short URL, redirect a short code to the original
URL, and provide basic analytics.

### 2.2 My interpretation

I separated the requirement into creation, resolution, analytics, validation,
persistence, and error handling. I intentionally started with H2 and one
application instance because the first objective was a correct and reviewable
backend.

### 2.3 Task decomposition

1. Define the `ShortUrl` entity.
2. Define the API DTO records.
3. Implement `ShortCodeGenerator`.
4. Implement `ShortUrlRepository`.
5. Implement `UrlShortenerService`.
6. Implement `UrlController`.
7. Implement `RedirectController`.
8. Implement domain exceptions and `GlobalExceptionHandler`.
9. Add unit, controller, and integration tests.
10. Run the complete Maven test suite.

### 2.4 Acceptance criteria

- A valid URL returns `201 Created`.
- The response contains a seven-character Base62 `shortCode`.
- A valid redirect returns `302 Found`.
- The `Location` header contains the original URL.
- Each successful redirect increments analytics.
- An unknown code returns `404 Not Found`.
- Invalid input returns `400 Bad Request`.
- Five code collisions result in `ShortCodeGenerationException`.

### 2.5 Code evidence

The final source contains:

- `ShortUrl`
- `ShortUrlRepository`
- `ShortCodeGenerator`
- `UrlShortenerService`
- `UrlController`
- `RedirectController`
- the four DTO records
- the domain exceptions
- `GlobalExceptionHandler`

### 2.6 Validation evidence

The source contains unit tests for generation, uniqueness attempts, creation,
resolution, analytics, and error conditions. The integration test
`createShortUrlThenRedirectTracksClickCount` validates creation, redirect,
and click counts of one and two.

## 3. Scenario 2: Brownfield expiration

### 3.1 Requirement

The existing shortener must support optional expiration without breaking
existing clients.

### 3.2 Impact analysis

The change affected:

- `ShortUrl.expiresAt`
- `CreateShortUrlRequest.expiresAt`
- `CreateShortUrlResponse.expiresAt`
- `UrlAnalyticsResponse.expiresAt`
- `UrlShortenerService`
- `ShortUrlRepository.incrementClickCount`
- `ShortUrlExpiredException`
- `GlobalExceptionHandler`
- service and integration tests
- Redis TTL calculation

### 3.3 Regression risks

- Existing requests without expiration could fail.
- The service could increment analytics before rejecting an expired code.
- A cache entry could outlive the link.
- An expired mapping could be incorrectly treated as missing.
- A previously shared code could be reused.

### 3.4 Engineering decisions

I kept `expiresAt` optional, used `Instant`, returned `410 Gone`, retained
analytics, prohibited automatic code reuse, and bounded cache TTL by the
remaining lifetime.

### 3.5 Acceptance criteria

- A request without `expiresAt` still returns `201 Created`.
- A future `expiresAt` is accepted and returned.
- A past `expiresAt` returns `400 Bad Request`.
- An expired redirect returns `410 Gone`.
- An expired redirect leaves `clickCount` unchanged.
- Analytics for the expired mapping remains available.

### 3.6 Validation evidence

`UrlShortenerIntegrationTest` contains:

- `createShortUrlWithFutureExpiresAtIsAcceptedAndReturned`
- `createShortUrlWithPastExpiresAtIsRejected`
- `redirectWithExpiredShortCodeReturnsGoneAndDoesNotIncrementClickCount`

## 4. Scenario 3: Ambiguous memorable URLs

### 4.1 Original ambiguity

“Memorable URL” did not define whether the system should generate words,
accept user-selected aliases, preserve case, allow symbols, or recycle old
aliases.

### 4.2 Questions resolved before implementation

- Who chooses the alias?
- Is the alias case-sensitive?
- What characters are allowed?
- What length is acceptable?
- Which routes are reserved?
- What happens when the alias already exists?
- Can an expired or inactive alias be reused?
- How does the alias coexist with generated Base62 codes?

### 4.3 Final interpretation

The system accepts an optional user-selected alias. The alias is trimmed and
lowercased. It must contain 4 to 30 lowercase letters, digits, hyphens, or
underscores. Duplicate and reserved aliases return `409 Conflict`. Existing
aliases are not recycled.

### 4.4 Impacted code

- `CreateShortUrlRequest.customAlias`
- `UrlShortenerService.RESERVED_ALIASES`
- `UrlShortenerService.reserveCustomAlias`
- `CustomAliasConflictException`
- `GlobalExceptionHandler`
- service, controller, and integration tests
- the browser UI

### 4.5 Acceptance criteria

- A valid custom alias returns `201 Created`.
- `My-Custom-Alias` is stored and returned as `my-custom-alias`.
- The alias redirects to the original URL.
- An alias shorter than four characters returns `400 Bad Request`.
- Unsupported characters return `400 Bad Request`.
- A duplicate alias returns `409 Conflict`.
- A reserved alias returns `409 Conflict`.
- A deactivated alias cannot be reused.
- `ShortCodeGenerator` is not called when a custom alias is supplied.

### 4.6 Validation evidence

The final tests cover normalization, redirect behavior, duplicate aliases,
reserved aliases, invalid aliases, a save-time race, and attempted reuse of a
deactivated alias.

## 5. Additional engineering enhancement: scalable deployment

### 5.1 Requirement

The final prototype should demonstrate a credible evolution toward better
redirect performance, shared state, and multiple application instances.

### 5.2 Decomposition

1. Preserve H2 as the default profile.
2. Add a PostgreSQL scalable profile.
3. Add the `RedirectCache` abstraction.
4. Add `NoOpRedirectCache`.
5. Add `RedisRedirectCache`.
6. Update `UrlShortenerService` for cache-aside.
7. Add `InstanceHeaderFilter`.
8. Add two application services.
9. Add NGINX.
10. Add Docker Compose.
11. Validate cross-instance behavior.

### 5.3 Acceptance criteria

- Local mode starts without PostgreSQL or Redis.
- The scalable profile connects to PostgreSQL.
- Redis is enabled only when `app.cache.enabled=true`.
- Redis failures do not intentionally fail a valid database-backed redirect.
- A cache hit still increments analytics.
- A stale cache entry is evicted.
- Both app instances share the same persisted mappings.
- NGINX exposes one public endpoint.
- Responses show `X-App-Instance`.
- A mapping created by one instance can be resolved by the other.

### 5.4 Recorded evidence

A creation request through NGINX returned:

```text
HTTP/1.1 201
X-App-Instance: app1
shortCode: GRTqfd3
```

The redirect request returned:

```text
HTTP/1.1 302
X-App-Instance: app2
Location: https://example.com/final-scalable-test
```

This evidence supports request distribution and shared persistence. It does
not prove a latency target, throughput target, automatic failover, database
replication, or a production availability percentage.

## 6. Automated test strategy

The supplied source contains 37 `@Test` methods in six test classes.

| Test class | Test count | Main purpose |
|---|---:|---|
| `UrlShortenerApplicationTests` | 1 | It verifies that the Spring context loads. |
| `ShortCodeGeneratorTest` | 3 | It verifies length, alphabet, and basic variation. |
| `UrlShortenerServiceTest` | 16 | It verifies core business rules, aliases, expiration, analytics, and cache paths. |
| `UrlControllerTest` | 7 | It verifies API validation, success, conflict, and not-found responses. |
| `UrlShortenerIntegrationTest` | 9 | It verifies end-to-end creation, redirect, analytics, aliases, and expiration. |
| `InstanceHeaderFilterTest` | 1 | It verifies `X-App-Instance`. |

The final quality gate is:

```bash
./mvnw clean test
```

The final repository should retain the successful terminal output before
submission.

## 7. Manual validation strategy

### 7.1 Required evidence

```bash
docker compose ps
curl -i http://localhost:8080/actuator/health
```

Repeated request distribution:

```bash
for i in {1..10}; do
  curl -s -D - http://localhost:8080/actuator/health \
    -o /dev/null | grep -i X-App-Instance
done
```

Creation, redirect, and analytics:

```bash
curl -i -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl":"https://example.com/final-check"}'

curl -i http://localhost:8080/YOUR_CODE

curl -i http://localhost:8080/api/v1/urls/YOUR_CODE/analytics
```

### 7.2 Additional validation that must not be claimed without output

Redis inspection:

```bash
docker compose exec redis redis-cli GET redirect:YOUR_CODE
```

Redis failure fallback:

```bash
docker compose stop redis
curl -i http://localhost:8080/YOUR_CODE
docker compose start redis
```

Application-instance failover:

```bash
docker compose stop app1
curl -i http://localhost:8080/actuator/health
curl -i http://localhost:8080/YOUR_CODE
docker compose start app1
```

## 8. Risks and trade-offs

### 8.1 Random-code collision

Seven-character Base62 provides a large namespace, but collision probability
is not zero. The database unique constraint is the final data-integrity
control.

### 8.2 Generated-code save race

The current code checks `existsByShortCode` before save. A concurrent insert
can occur between those operations. The generated-code path does not retry
the save-time uniqueness failure. This is a known hardening item.

### 8.3 Custom-alias race

The custom-alias path catches `DataIntegrityViolationException` and returns
`CustomAliasConflictException`. This handles the race where two requests
pass the existence check but only one insert succeeds.

### 8.4 Cache-miss state race

On a cache miss, the service checks the entity and then calls the conditional
analytics update without inspecting the returned row count. A concurrent
deactivation or expiration could occur between those operations. The service
should require one updated row before caching or redirecting.

### 8.5 Synchronous analytics

Every successful redirect writes to PostgreSQL. This provides immediate
analytics consistency but reduces the database-load benefit of Redis. An
asynchronous analytics pipeline is a future option if measurements justify
the added complexity.

### 8.6 H2 and PostgreSQL differences

H2 provides fast local development but does not reproduce every PostgreSQL
behavior. The scalable profile and integration testing reduce this risk, but
production migrations should use a dedicated migration tool.

### 8.7 Single infrastructure services

The local Docker environment demonstrates multiple application instances,
but NGINX, PostgreSQL, and Redis are each single services. The prototype
therefore does not establish production high availability.

### 8.8 Security and abuse

The prototype lacks authentication, ownership, rate limits, malicious-link
screening, TLS termination, and production secret storage. It must not be
treated as a safe public shortening service without those controls.

### 8.9 Alias no-reuse

No reuse protects the historical meaning of a shared URL, but it permanently
consumes aliases even after deactivation or expiration.

### 8.10 Schema management

`spring.jpa.hibernate.ddl-auto=update` is acceptable for the demonstration
but is not appropriate as the long-term production migration strategy.

## 9. Validation status

| Item | Status |
|---|---|
| Core source implementation | Verified in the supplied source. |
| Thirty-seven test methods | Verified in the supplied source. |
| Create request through NGINX | Verified by recorded output. |
| Redirect through a different instance | Verified by recorded output. |
| Shared persisted mapping | Supported by the cross-instance result. |
| Redis code fallback behavior | Implemented in `RedisRedirectCache`; final runtime evidence should be retained if tested. |
| Automatic application failover | It must not be claimed unless the stop-instance test output is retained. |
| Load and latency targets | They were not benchmarked. |
| Production availability | It was not demonstrated. |
| Security assessment | It was not performed. |
