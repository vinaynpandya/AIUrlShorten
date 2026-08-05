# Testing Strategy — URL Shortener

## Unit tests

`ShortUrlService` business logic — input validation orchestration,
alias-vs-generated-code branching, collision-retry loop, atomic
click-count update, and expiry checks — is covered with JUnit 5 and
Mockito, isolating the service from the Spring context and the database.

## Controller tests

`UrlController` and `RedirectController` are covered with MockMvc slice
tests, exercising the full request/response cycle including validation
failures and the exception-handler status codes (`400`, `404`, `409`,
`410`).

## Integration tests

Persistence-layer behavior — the unique constraint on `short_code` and
the atomic `@Modifying` click-count update — is covered against the
embedded H2 database used by the default profile.

## Manual API validation

Automated tests run under the default profile (H2, Redis cache disabled)
and do not exercise the `scalable` Docker Compose profile. The
`scalable` deployment (PostgreSQL, Redis, NGINX, `app1`/`app2`) was
therefore validated manually:

- URL creation through NGINX (`POST http://localhost:8080/api/v1/urls`)
  returned `HTTP 201`, served by `app1` (identified via the
  `X-App-Instance` response header).
- Redirect through NGINX (`GET http://localhost:8080/{shortCode}`)
  returned `HTTP 302`, served by `app2`.
- Together, these verified NGINX load balancing across `app1`/`app2` and
  confirmed both instances share the same PostgreSQL persistence layer —
  a code created via one instance resolved correctly via the other.
- `app1` and `app2` were also confirmed directly reachable on `8081` and
  `8082` respectively, bypassing NGINX, for isolating instance-specific
  behavior during verification.

## Negative testing

Covered by unit and controller tests: malformed/blank `originalUrl`
(`400`), unknown `shortCode` (`404`), duplicate custom alias (`409`),
and redirect on an expired code (`410`).

## Regression testing

The full suite (`./mvnw clean test`) is re-run after each brownfield or
ambiguous-requirement change (see `docs/scenarios.md`) to confirm
existing create/redirect/analytics behavior is unaffected.

## Quality gates

- `./mvnw clean test` must pass before any change is considered
  complete.
- The `scalable` profile is validated manually, as above, since Maven
  tests run only against the default (H2, no-cache) profile and do not
  exercise NGINX, Redis, or PostgreSQL.
- No load, performance, or availability testing has been performed to
  date; `scalable`-profile validation is functional/correctness
  verification only (HTTP status codes and instance routing via
  `X-App-Instance`), not a benchmark.
