## Greenfield Scenario — Core URL Shortener

### Intent

Build the initial URL-shortening system from scratch with URL creation,
redirection, basic analytics, validation and error handling.

### Task decomposition

1. Create the persistence entity and repository.
2. Implement seven-character Base62 code generation.
3. Define API request and response DTOs.
4. Implement URL creation and redirect services.
5. Add atomic click-count updates.
6. Add REST controllers and global exception handling.
7. Perform manual end-to-end testing.
8. Add unit, controller and integration tests.

### AI contribution

Claude Code assisted with:

- Entity and repository implementation.
- Short-code generator.
- DTO generation.
- Service-layer implementation.
- Controller and exception-handler implementation.
- Unit and integration test generation.

### Engineer decisions

- Selected Java 17, Spring Boot and Maven.
- Selected H2 for the runnable prototype.
- Used seven-character Base62 codes.
- Required bounded collision retries.
- Required a unique database constraint.
- Required atomic analytics updates.
- Used HTTP 302 redirects.
- Accepted only HTTP and HTTPS URLs.
- Kept authentication and rate limiting outside Phase 1.

### Engineer oversight

- Approved file changes individually.
- Prevented Claude from making broad repository changes.
- Rejected unrelated formatting changes.
- Stopped Claude when it moved outside the requested task boundary.
- Corrected outdated Spring Boot test imports.
- Reviewed generated code and test results.
- Performed manual API validation.

### Validation

- URL creation returned HTTP 201.
- Redirect returned HTTP 302.
- The Location header contained the original URL.
- Analytics increased after redirects.
- Blank and malformed URLs returned HTTP 400.
- Unknown codes returned HTTP 404.
- Maven unit and integration tests passed.

### Status

Completed.

## Brownfield Scenario — URL Expiration

### Original requirement

Allow shortened URLs to expire.

### Impact analysis

The change affected the persisted URL model, API DTOs, redirect service,
exception handling and regression tests.

### Regression risks

- Existing requests without expiration could stop working.
- Expired redirects could incorrectly increment analytics.
- Incorrect UTC handling could expire links too early or too late.
- Expiration changes could alter existing response contracts.
- Expired short codes could accidentally be reused.

### AI contribution

Claude assisted with impact analysis, implementation and regression-test
generation.

### Engineer oversight

- Required optional expiration for backward compatibility.
- Required UTC Instant timestamps.
- Required expiration validation.
- Required HTTP 410 Gone.
- Required expiration checks before analytics updates.
- Confirmed that codes are never recycled.

### Validation

- Existing non-expiring URLs continued working.
- Future expiration was accepted.
- Past expiration returned HTTP 400.
- Expired redirect returned HTTP 410.
- Expired redirect did not increase analytics.
- Full regression suite passed.

### Status

Completed.

## Ambiguous Scenario — Memorable URLs

### Original requirement

Users should be able to create memorable short URLs.

### Ambiguities identified

- Whether memorable meant custom aliases.
- Allowed characters.
- Minimum and maximum length.
- Case sensitivity.
- Reserved routes.
- Duplicate handling.
- Reuse after expiration.

### Approved interpretation

- Memorable URLs are optional custom aliases.
- Aliases contain letters, digits, hyphens or underscores.
- Length is 4 to 30 characters.
- Aliases are normalized to lowercase.
- Duplicate aliases return HTTP 409.
- Reserved application routes are rejected.
- Aliases are never recycled.

### AI contribution

Claude assisted with ambiguity identification, implementation and tests.

### Engineer oversight

The engineer normalized the requirement, approved the validation rules,
identified route-collision risks and verified backward compatibility.

### Validation

- Valid custom alias returned HTTP 201.
- Alias redirect returned HTTP 302.
- Case-insensitive duplicate returned HTTP 409.
- Reserved alias returned HTTP 400.
- Invalid characters returned HTTP 400.
- Generated short codes continued working.
- Full regression suite passed.

### Status

Completed.

## Scalable Deployment — PostgreSQL, Redis and NGINX

### Intent

Evolve the runnable prototype into a horizontally scaled deployment:
PostgreSQL as the durable source of truth, an optional Redis cache-aside
layer for redirects, two application instances, and an NGINX load
balancer as the public entry point — without changing the application's
existing request/response contract.

### Task decomposition

1. Add a `scalable` Spring profile configured for PostgreSQL and Redis.
2. Implement a cache-aside redirect path (`RedisRedirectCache`) with a
   `NoOpRedirectCache` fallback when caching is disabled.
3. Add an `X-App-Instance` response header (`InstanceHeaderFilter`) to
   identify the serving instance.
4. Add `docker-compose.yml` services for `postgres`, `redis`, `app1`,
   `app2`, and `nginx`.
5. Configure NGINX as a load balancer across `app1`/`app2`.
6. Manually verify request distribution and shared persistence.

### AI contribution

Claude assisted with the `RedisRedirectCache`/`NoOpRedirectCache`
cache-aside implementation, the `InstanceHeaderFilter`, the
`docker-compose.yml` service definitions, the NGINX load-balancer
configuration, and documentation updates reflecting the implemented
deployment.

### Engineer decisions

- Selected PostgreSQL and Redis for the `scalable` profile.
- Required Redis to be optional, with PostgreSQL as the sole source of
  truth.
- Required the cache-aside pattern, not read-through/write-through.
- Required NGINX as the single public entry point, with `app1`/`app2`
  also directly reachable on `8081`/`8082` for diagnostics.
- Required an `X-App-Instance` header for observability of
  load-balancing behavior.

### Engineer oversight

- Reviewed the cache-aside fallback logic for correctness when Redis is
  unavailable.
- Confirmed the default (local) profile is unaffected — caching remains
  disabled and H2 remains the database.
- Performed manual verification of the deployed stack.

### Validation

- URL creation through NGINX returned HTTP 201, served by `app1`.
- Redirect through NGINX returned HTTP 302, served by `app2`.
- These results verified NGINX load balancing and shared PostgreSQL
  persistence across instances.
- No load, performance, or availability testing has been performed;
  validation to date is functional/correctness verification only.

### Status

Completed (functional verification only; no performance or failover
testing to date).