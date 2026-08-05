# Engineering Scenarios — URL Shortener

Three scenarios illustrating how the same architecture (see
`architecture.md`) is worked through greenfield, brownfield, and
ambiguous-requirement conditions. Each follows the project's
engineering workflow: inspect → plan → get engineer approval →
implement → test → explain.

---

## Scenario 1 — Greenfield: Build URL Creation, Redirect, and Basic Analytics

### Original Requirement
Build URL creation, redirect, and basic analytics from scratch.

### Requirement Interpretation
Deliver the three core endpoints defined in `requirements-analysis.md`
§4: `POST /api/v1/urls` (create, with optional custom alias),
`GET /{shortCode}` (302 redirect), and
`GET /api/v1/urls/{shortCode}/analytics` (aggregate click stats).
Aggregate analytics only — `clickCount` and `lastAccessedAt` on the
`ShortUrl` entity, no per-click event log.

### Ambiguities and Assumptions
- "Basic analytics" is interpreted as aggregate counters, not a click
  history — per `requirements-analysis.md` §5.
- Short codes are 7-character Base62, case-sensitive.
- No default expiration in this scenario (expiration is Scenario 2).
- Custom alias support is included here as a first-class create-time
  option (its *interpretation* as "memorable URLs" is Scenario 3's
  concern; here it is simply a documented optional field).
- Returned short URL is built from `app.base-url` config, not the
  request `Host` header (security decision, `architecture.md` §10).

### Task Decomposition
1. `ShortUrl` JPA entity + Flyway/DDL-equivalent schema (via
   `spring.jpa.hibernate.ddl-auto` for the prototype) with unique
   index on `short_code`.
2. Request/response DTOs: `CreateUrlRequest`, `CreateUrlResponse`,
   `AnalyticsResponse`, with Jakarta Validation annotations.
3. `ShortCodeGenerator` utility (Base62, 7 chars).
4. `ShortUrlRepository` (Spring Data JPA) including the atomic
   `@Modifying` click-increment query.
5. Domain exceptions: `ShortUrlNotFoundException`,
   `AliasAlreadyExistsException`, `InvalidUrlException`.
6. `ShortUrlService`: create logic (validation, alias/generate branch,
   collision-retry loop), resolve logic (lookup + atomic increment),
   analytics lookup.
7. `UrlController` (create, analytics) and `RedirectController`
   (redirect).
8. `GlobalExceptionHandler` (`@RestControllerAdvice`).
9. Unit tests (`ShortUrlService` with Mockito) and slice tests
   (`UrlController`/`RedirectController` with MockMvc).

### Task Dependencies and Sequence
Entity/schema → DTOs → generator util → repository → exceptions →
service → controllers → exception handler → tests. Controllers cannot
be meaningfully tested until the service contract is stable;
collision-retry logic in the service depends on the repository's
unique-constraint behavior, so repository work precedes it.

### Expected Files or Modules Affected
- `entity/ShortUrl.java`
- `dto/CreateUrlRequest.java`, `dto/CreateUrlResponse.java`, `dto/AnalyticsResponse.java`
- `util/ShortCodeGenerator.java`
- `repository/ShortUrlRepository.java`
- `exception/*.java`, `exception/GlobalExceptionHandler.java`
- `service/ShortUrlService.java`
- `controller/UrlController.java`, `controller/RedirectController.java`
- `application.properties` (`app.base-url`, H2 config)
- Corresponding test classes under `src/test/java`

### Acceptance Criteria
- Matches `requirements-analysis.md` §8 items 1–4 and 6: `201` with
  resolvable code on valid input; `409` + no record on duplicate
  alias; `400` + no record on malformed URL; `302` with correct
  `Location` on valid redirect; analytics returns current
  `clickCount`/`lastAccessedAt`, with `lastAccessedAt` null until the
  first redirect.

### Risks and Failure Scenarios
- Base62 collision on generated code → mitigated by unique-constraint
  detection + bounded retry (`architecture.md` §8); retry exhaustion
  is an accepted `500` at prototype scale.
- Concurrent redirects on the same code losing click increments →
  mitigated by atomic `UPDATE` (`architecture.md` §9), not
  application-level read-modify-write.
- Malformed `app.base-url` misconfiguration producing broken returned
  URLs → caught by a slice test asserting the response URL shape.

### Testing and Validation
- Unit tests: alias conflict → `409`; invalid URL → `400`; generated
  collision → retry succeeds; expiry not yet applicable in this
  scenario.
- MockMvc slice tests: full request/response cycle for all three
  endpoints, including error-path status codes.
- Manual: `./mvnw clean test`, then run and exercise via curl.

### AI Contribution
AI proposes entity/DTO/service/controller code and tests following
the approved architecture, and explains the collision-handling and
atomic-update logic inline where non-obvious.

### Engineer Review and Approval Responsibility
Engineer reviews the plan (this document) before implementation,
reviews the generated code and tests for correctness and security
before merge, and runs `./mvnw clean test` locally to confirm. No
change is committed without explicit engineer approval.

---

## Scenario 2 — Brownfield: Add Optional URL Expiration

### Original Requirement
Add optional URL expiration to the already-working application.

### Requirement Interpretation
Extend the existing `ShortUrl` entity and create flow with an optional
`expiresAt` field (already present in the data model per
`requirements-analysis.md` §3, but not yet enforced). Extend the
redirect flow to reject expired codes with `410 Gone`, per
`requirements-analysis.md` §4/§6. Analytics and creation for
non-expiring URLs remain unchanged — this is additive, not a rewrite.

### Ambiguities and Assumptions
- No default expiration: URLs remain non-expiring unless `expiresAt`
  is explicitly supplied at creation (`requirements-analysis.md` §5).
- `expiresAt` is UTC and must be strictly in the future at creation
  time (assumption: reject past-dated `expiresAt` at creation with
  `400`, rather than silently accepting an already-expired URL).
- Expiry check happens only on redirect, not on the analytics
  endpoint — an expired code's analytics remain queryable (assumption,
  since the requirement only specifies redirect behavior for expiry).
- No update/extend-expiration endpoint is in scope; expiration is
  set-once at creation.

### Task Decomposition
1. Add `@Future`-style validation for optional `expiresAt` on
   `CreateUrlRequest` (already a DTO field per Scenario 1's schema,
   validation was previously absent since expiry wasn't enforced).
2. Add `ShortUrlExpiredException` (if not already present from
   Scenario 1's exception set) and wire it into
   `GlobalExceptionHandler` → `410`.
3. Update `ShortUrlService.resolve()` to check `expiresAt` against the
   current time before the atomic click-increment, short-circuiting
   with `ShortUrlExpiredException` if past.
4. Confirm `CreateUrlResponse`/`AnalyticsResponse` surface `expiresAt`
   where relevant so clients can see it.
5. Add tests: creation with future expiry, creation rejecting past
   expiry, redirect on non-expired code (unchanged behavior), redirect
   on expired code (`410`), analytics on expired code still `200`.

### Task Dependencies and Sequence
Validation on create → exception addition → service resolve-path
check → response DTO confirmation → tests. This sequence is chosen so
the create-time guard (reject past-dated expiry) exists before the
redirect-time check is exercised, and so existing non-expiring-URL
tests are re-run first to confirm no regression before new
expiry-specific tests are added.

### Expected Files or Modules Affected
- `dto/CreateUrlRequest.java` (add/confirm `expiresAt` validation)
- `exception/ShortUrlExpiredException.java` (new, if not already present)
- `exception/GlobalExceptionHandler.java` (add `410` mapping)
- `service/ShortUrlService.java` (expiry check in `resolve()`)
- Existing test classes for `ShortUrlService` and `RedirectController`
  (extended, not replaced)

No changes expected to `ShortUrlRepository`, `ShortCodeGenerator`, or
the analytics path — this is the brownfield constraint: touch only
what expiration requires.

### Acceptance Criteria
Matches `requirements-analysis.md` §8: redirect on an expired code
returns `410`; redirect on a valid, non-expired code is unaffected
(regression check); analytics endpoint remains reachable and correct
for expired codes.

### Risks and Failure Scenarios
- Regression risk: expiry check placed incorrectly could break the
  non-expiring-URL redirect path (the majority case) — mitigated by
  re-running the full existing test suite, not just new tests.
  timezone/UTC mismatch between stored `expiresAt` and server clock
  causing off-by-one expiry decisions — mitigated by consistently
  using UTC throughout, as already established in the data model.
- Expiry check ordering: must happen *before* the click-increment, or
  an expired code would still record a click before being rejected —
  explicitly sequenced in Task Decomposition step 3.

### Testing and Validation
- Unit tests: expired-code resolve throws `ShortUrlExpiredException`;
  non-expired/no-expiry resolve unaffected; create rejects past-dated
  `expiresAt`.
- MockMvc: redirect on expired code returns `410` with no
  `Location` header change to the original flow; existing `302`/`404`
  tests re-run unchanged to confirm no regression.
- `./mvnw clean test` run in full (not filtered) to catch regressions
  in the greenfield suite.

### AI Contribution
AI proposes the minimal diff needed for expiry enforcement, explicitly
flags the create-time-vs-redirect-time ordering assumption for
engineer sign-off, and adds/extends tests without touching unrelated
passing tests.

### Engineer Review and Approval Responsibility
Engineer confirms the past-dated-expiry rejection assumption is
correct before implementation, reviews the diff to confirm it is
additive only (per CLAUDE.md's "no unrelated refactoring" rule), and
re-runs the full test suite to confirm no regression in the existing
create/redirect/analytics behavior.

---

## Scenario 3 — Ambiguous: "Users Should Be Able to Create Memorable URLs"

### Original Requirement
"Users should be able to create memorable URLs."

### Requirement Interpretation
Interpreted as: users may optionally supply a **custom alias** at
creation time instead of receiving a system-generated Base62 code,
per `requirements-analysis.md` §1 and §5. "Memorable" is read as
*human-chosen and readable*, not as a system-side memorability
algorithm (e.g., dictionary-word generation) — the latter is
explicitly out of scope unless the engineer redirects this
interpretation.

### Ambiguities and Assumptions
- The requirement does not define what "memorable" means
  operationally; this is the central ambiguity. The chosen
  interpretation (optional custom alias) is the narrowest reading
  consistent with the existing API design in
  `requirements-analysis.md` §4, and is flagged as an assumption
  requiring explicit engineer confirmation, not silently adopted.
- Alias format assumed alphanumeric plus `-`/`_`, 3–20 characters
  (`requirements-analysis.md` §5) — an arbitrary but documented
  choice; engineer may prefer different bounds.
- Aliases are case-sensitive, consistent with generated codes.
- No alias reservation/moderation (e.g., blocking offensive or
  trademarked aliases) — out of scope for the prototype, noted as a
  production consideration.
- No alias-rename/transfer capability — set-once at creation, same as
  expiration in Scenario 2.

### Task Decomposition
1. Confirm interpretation with engineer before implementation (this
   is the ambiguity-resolution step itself, not a coding task).
2. Add `customAlias` optional field to `CreateUrlRequest` with
   `@Pattern`/length validation.
3. Extend `ShortUrlService.create()` with the alias-vs-generate branch
   (present conceptually in `architecture.md` §4, implemented here):
   if alias supplied, validate format and uniqueness; else fall back
   to `ShortCodeGenerator`.
4. Add `AliasAlreadyExistsException` → `409` mapping (if not already
   present from Scenario 1).
5. Tests: valid alias creates successfully; duplicate alias → `409`,
   no record created; invalid alias format → `400`; omitted alias
   falls back to generated code (existing behavior unaffected).

### Task Dependencies and Sequence
Interpretation confirmation (blocking — nothing else proceeds without
it) → DTO field/validation → service branch logic → exception mapping
→ tests. The confirmation step is first and gating because building
against the wrong interpretation of "memorable" would waste the
remaining steps.

### Expected Files or Modules Affected
- `dto/CreateUrlRequest.java` (add `customAlias` field + validation)
- `service/ShortUrlService.java` (alias branch in `create()`)
- `exception/AliasAlreadyExistsException.java` (new, if not already
  present)
- `exception/GlobalExceptionHandler.java` (`409` mapping)
- Test classes for `ShortUrlService` and `UrlController` (extended)

### Acceptance Criteria
Matches `requirements-analysis.md` §8: valid alias submission
succeeds with `201` and a resolvable short URL using the supplied
alias; duplicate alias returns `409` and creates no record; generated
codes remain unaffected when no alias is supplied.

### Risks and Failure Scenarios
- Misinterpreting "memorable" (e.g., building a word-based generator
  instead of accepting user-supplied aliases) would deliver the wrong
  feature entirely — mitigated by the mandatory confirmation step
  before implementation.
- Alias/generated-code namespace collision: a custom alias could
  coincidentally match a future generated code, or vice versa — since
  both live in the same `short_code` unique column
  (`architecture.md` §6), the existing unique constraint naturally
  prevents this; no separate namespace is needed.
- Alias enumeration/guessing (a form of abuse) — acknowledged as a
  production rate-limiting concern (`requirements-analysis.md` §10),
  not addressed in the prototype.

### Testing and Validation
- Unit tests: alias creation success; duplicate alias `409`; malformed
  alias `400`; no-alias path still generates a Base62 code correctly
  (regression check against Scenario 1 behavior).
- MockMvc: end-to-end create-with-alias then redirect-via-alias flow.
- `./mvnw clean test` for full-suite regression confirmation.

### AI Contribution
AI surfaces the ambiguity explicitly (as done in this document) rather
than silently picking an interpretation, proposes the narrowest
reading tied to existing requirements, and implements only after
interpretation is confirmed.

### Engineer Review and Approval Responsibility
Engineer is the sole authority on resolving the "memorable" ambiguity
— AI's interpretation is a proposal, not a decision. Engineer approves
the interpretation before any code is written, then reviews the
resulting diff and tests as with the other scenarios.

---

## Scenario 4 — Brownfield: Scalable Deployment (PostgreSQL, Redis, NGINX)

### Original Requirement
Evolve the running prototype into a horizontally scaled deployment
without changing its existing API contract.

### Requirement Interpretation
Add a `scalable` Spring profile and a Docker Compose stack: PostgreSQL
as the durable source of truth, an optional Redis cache-aside layer in
front of the redirect path, two application instances (`app1`, `app2`),
and NGINX as the public load-balancing entry point. The default
(local/H2) profile is left unchanged — this is additive, matching the
brownfield constraint from Scenario 2.

### Ambiguities and Assumptions
- Redis is treated as optional infrastructure, not a second source of
  truth: PostgreSQL remains authoritative, and a Redis outage or miss
  falls back to a direct PostgreSQL read rather than failing the
  request.
- Caching applies to the redirect path only — URL creation and
  analytics always read/write PostgreSQL directly, since those are
  low-volume relative to redirects.
- `app1`/`app2` are assumed stateless and interchangeable; nothing is
  instance-local, so either can serve any request as long as both point
  at the same `postgres`/`redis` containers.
- Direct per-instance ports (`8081`, `8082`) are exposed for diagnostics
  alongside NGINX (`8080`), not as an alternate client-facing path.

### Task Decomposition
1. `application-scalable.properties`: PostgreSQL and Redis connection
   properties sourced from environment variables.
2. `RedisRedirectCache` (cache-aside) and `NoOpRedirectCache` (disabled
   default), selected by `app.cache.enabled`.
3. `InstanceHeaderFilter`: sets `X-App-Instance` from
   `app.instance-name` on every response.
4. `docker-compose.yml`: `postgres`, `redis`, `app1`, `app2`, `nginx`
   services, with `app1`/`app2` sharing the same `postgres`/`redis`.
5. `infra/nginx/nginx.conf`: `least_conn` load balancing across
   `app1:8080`/`app2:8080` with passive health checks.
6. Manual verification of request distribution and shared persistence
   through the deployed stack.

### Task Dependencies and Sequence
Profile/connection config → cache abstraction (interface + two
implementations) → instance-identification filter → Compose service
definitions → NGINX config → manual verification. The cache
implementations must exist before Compose can wire
`REDIS_HOST`/`REDIS_PORT` into a running container; NGINX config depends
on both `app1` and `app2` already being defined as Compose services.

### Expected Files or Modules Affected
- `src/main/resources/application-scalable.properties` (new)
- `src/main/java/.../cache/RedirectCache.java`,
  `RedisRedirectCache.java`, `NoOpRedirectCache.java` (new)
- `src/main/java/.../config/InstanceHeaderFilter.java` (new)
- `docker-compose.yml`, `infra/nginx/nginx.conf`, `Dockerfile` (new)
- `.env.example` (new, non-production placeholder credentials)

No changes to `ShortUrlService`'s create/redirect/analytics business
logic itself, aside from routing redirect reads through the cache-aside
`RedirectCache` abstraction — the API contract (`201`/`302`/`404`/`409`/
`410`) is unchanged.

### Acceptance Criteria
- URL creation and redirect through NGINX return the same status codes
  as the default profile (`201`, `302`, `404`, `409`, `410`).
- Requests are observably distributed across `app1`/`app2` (via
  `X-App-Instance`).
- A URL created via one instance is resolvable via the other, proving
  shared PostgreSQL persistence.
- The default profile's behavior and test suite are unaffected.

### Risks and Failure Scenarios
See `docs/risks-and-tradeoffs.md` ("Single-instance availability", "No
distributed cache", "Concurrent insert race", "Synchronous analytics
latency") for the full analysis of how this scenario changes — or
leaves unchanged — each existing risk.

### Testing and Validation
- `./mvnw clean test`: full existing suite re-run to confirm no
  regression in the default profile (this suite does not exercise
  PostgreSQL/Redis/NGINX directly).
- Manual, against the deployed `scalable` stack:
  - URL creation through NGINX (`POST http://localhost:8080/api/v1/urls`)
    returned `HTTP 201`, served by `app1`.
  - Redirect through NGINX (`GET http://localhost:8080/{shortCode}`)
    returned `HTTP 302`, served by `app2`.
  - These two results verified NGINX load balancing and shared
    PostgreSQL persistence across instances.
- No load, performance, or availability (failover) testing has been
  performed; validation to date is functional/correctness verification
  only — see `docs/testing-strategy.md`.

### AI Contribution
AI proposed the cache-aside abstraction (interface plus enabled/disabled
implementations) so the same codebase serves both profiles unchanged,
implemented the Compose/NGINX configuration, and documented the
resulting deployment; it did not propose or record any performance or
availability figures beyond the functional checks the engineer directed.

### Engineer Review and Approval Responsibility
Engineer confirmed Redis should remain optional with PostgreSQL as sole
source of truth, reviewed the cache-aside fallback logic for correctness
under Redis unavailability, and performed the manual verification of the
deployed stack recorded above.
