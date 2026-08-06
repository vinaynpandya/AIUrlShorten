# Scenarios and Validation

Reformatted from `docs/03-scenarios-validation-and-risks.md` §2–4, with one
new scenario (soft-delete) documented from the current code and tests. No
scenario, requirement, or feature described here goes beyond what exists in
`docs/03-scenarios-validation-and-risks.md` or the current codebase.

## Scenario 1 — Greenfield: core shortener

### Requirement
The system must create a short URL, redirect a short code to the original
URL, and provide basic analytics.

### Decomposition
Define the `ShortUrl` entity; define the API DTO records; implement
`ShortCodeGenerator`, `ShortUrlRepository`, `UrlShortenerService`,
`UrlController`, and `RedirectController`; implement domain exceptions and
`GlobalExceptionHandler`; add unit, controller, and integration tests; run
the full Maven test suite.

### Execution
`ShortUrl`, `ShortUrlRepository`, `ShortCodeGenerator`, `UrlShortenerService`,
`UrlController`, `RedirectController`, the four DTO records, the domain
exceptions, and `GlobalExceptionHandler` were implemented to satisfy: a valid
URL returns `201 Created` with a seven-character Base62 `shortCode`; a valid
redirect returns `302 Found` with a `Location` header containing the original
URL; each successful redirect increments analytics; an unknown code returns
`404 Not Found`; invalid input returns `400 Bad Request`; five code
collisions raise `ShortCodeGenerationException`.

### Validation Evidence
Unit tests cover generation, uniqueness attempts, creation, resolution,
analytics, and error conditions. The integration test
`createShortUrlThenRedirectTracksClickCount` validates creation, redirect,
and click counts of one and two.

## Scenario 2 — Brownfield: expiration

### Requirement
The existing shortener must support optional expiration without breaking
existing clients.

### Decomposition
Impact spanned `ShortUrl.expiresAt`, `CreateShortUrlRequest.expiresAt`,
`CreateShortUrlResponse.expiresAt`, `UrlAnalyticsResponse.expiresAt`,
`UrlShortenerService`, `ShortUrlRepository.incrementClickCount`,
`ShortUrlExpiredException`, `GlobalExceptionHandler`, service and
integration tests, and the Redis TTL calculation.

### Execution
`expiresAt` was kept optional, using `Instant` for UTC-safe handling. Past
values are rejected; expired redirects return `410 Gone`; expired redirects
do not increment analytics; expired codes are not reused; Redis TTL is
bounded by the remaining link lifetime. A request without `expiresAt` still
returns `201 Created`; a future `expiresAt` is accepted and returned; a past
`expiresAt` returns `400 Bad Request`.

### Validation Evidence
`UrlShortenerIntegrationTest` contains
`createShortUrlWithFutureExpiresAtIsAcceptedAndReturned`,
`createShortUrlWithPastExpiresAtIsRejected`, and
`redirectWithExpiredShortCodeReturnsGoneAndDoesNotIncrementClickCount`.

## Scenario 3 — Brownfield: soft-delete endpoint

### Requirement
Add a way to deactivate a short URL through the API. `ShortUrl.deactivate()`
and the `active` flag already existed and already governed redirect
eligibility (`resolveOriginalUrl` filters on `isActive()`), but no API path
reached `deactivate()` — it was only ever called from test fixtures.

### Decomposition
Add `DELETE /api/v1/urls/{shortCode}` to `UrlController`; add
`UrlShortenerService.deactivateShortUrl(String shortCode)`; add a new,
minimal exception `ShortUrlAlreadyInactiveException` for the
already-inactive case rather than force-fitting the existing
`CustomAliasConflictException`; map the new exception in
`GlobalExceptionHandler`; evict the short code from `RedirectCache` on
deactivation so a cached mapping cannot keep resolving after deactivation;
add unit and controller tests without modifying `resolveOriginalUrl` or
`getAnalytics`.

### Execution
`UrlShortenerService.deactivateShortUrl` looks up the `ShortUrl` by
`shortCode`, throwing `ShortUrlNotFoundException` (reused, same as
`getAnalytics`) if it doesn't exist. If found but already inactive, it
throws the new `ShortUrlAlreadyInactiveException`, mapped by
`GlobalExceptionHandler` to `409 Conflict` (same status-handling pattern as
`CustomAliasConflictException`, without reusing that class). Otherwise it
calls `shortUrl.deactivate()`, saves, and calls
`redirectCache.evict(shortCode)`. `UrlController.deactivateShortUrl` exposes
this as `DELETE /api/v1/urls/{shortCode}`, returning `204 No Content` on
success. `resolveOriginalUrl` was not modified — its existing
`.filter(ShortUrl::isActive)` check already causes a deactivated link to
return `404 Not Found` on redirect, so a deactivated short code fails the
same way it did before this change.

### Validation Evidence
Five tests were added:
- `UrlShortenerServiceTest.deactivateShortUrlSucceedsForActiveShortCode`
- `UrlShortenerServiceTest.deactivateShortUrlThrowsNotFoundWhenShortCodeMissing`
- `UrlShortenerServiceTest.deactivateShortUrlThrowsWhenAlreadyInactive`
- `UrlControllerTest.deactivateShortUrlReturnsNoContent`
- `UrlControllerTest.deactivateShortUrlWithUnknownShortCodeReturnsNotFound`

Full suite result at the time this scenario was implemented:
`./mvnw clean test` → **76 tests, 0 failures, 0 errors, BUILD SUCCESS**
(up from 71 before this change).

## Scenario 4 — Ambiguous requirement: custom alias

### Requirement
"Memorable URL" did not define whether the system should generate words,
accept user-selected aliases, preserve case, allow symbols, or recycle old
aliases.

### Decomposition
Questions resolved before implementation: who chooses the alias; is it
case-sensitive; what characters and length are allowed; which routes are
reserved; what happens on a duplicate; can an expired or inactive alias be
reused; how does it coexist with generated Base62 codes.

### Execution
The system accepts an optional user-selected alias, trimmed and lowercased,
4–30 characters of lowercase letters, digits, hyphens, or underscores.
Duplicate and reserved aliases return `409 Conflict`; existing aliases are
not recycled. Impacted code: `CreateShortUrlRequest.customAlias`,
`UrlShortenerService.RESERVED_ALIASES`,
`UrlShortenerService.reserveCustomAlias`, `CustomAliasConflictException`,
`GlobalExceptionHandler`, service/controller/integration tests, and the
browser UI.

### Validation Evidence
Tests cover normalization (`My-Custom-Alias` → `my-custom-alias`), redirect
behavior, duplicate aliases, reserved aliases, invalid aliases, a save-time
race, and attempted reuse of a deactivated alias.
