# AI Prompt and Engineering Decision Log

## 1. Purpose

This document records the prompt sequence that guided the project from the
initial design through the final scalable demonstration. The prompts are
written with the context, constraints, acceptance criteria, and code names
that were used as the implementation evolved.

For each phase, I recorded why I asked the question, how Claude contributed,
what I reviewed, and what engineering decision I retained.

## 2. Model selection

I used Claude Code with the Sonnet 5 model under a Claude Pro subscription.

I selected Sonnet 5 because most tasks were bounded Java and Spring Boot
work: architecture decomposition, DTO and service implementation, test
generation, debugging, configuration, Docker support, and documentation.
These tasks required strong coding capability but did not justify using the
most expensive model for every interaction.

I retained the option to use Opus 5 for an unusually difficult architecture
or reasoning problem, but I did not make model expense a substitute for
decomposition and review. I reduced difficult work into smaller decisions,
asked Claude to explain its impact, and reviewed each change before
acceptance.

## 3. Token and context strategy

I controlled token use through the following practices:

1. I divided the project into separate phases instead of requesting the
   complete final system in one prompt.
2. I used file allowlists after the relevant classes existed.
3. I asked Claude to present a plan before editing.
4. I prevented full-repository scans unless they were necessary.
5. I ran Git commands manually rather than asking Claude to manage history.
6. I diagnosed Docker, port, and environment problems through focused
   terminal commands.
7. I stored stable project rules in `CLAUDE.md` rather than repeating them.
8. I required concise changed-file and test summaries after each task.
9. I stopped a long documentation sequence when it was consuming excessive
   context and replaced it with one bounded documentation task.
10. I used Sonnet 5 for implementation because it provided an appropriate
    balance of capability, speed, and token cost.

## 4. Phase 1: Establish the complete project context

### My engineering objective

I wanted Claude to understand the assignment, the engineering expectations,
the technology constraints, and the approval process before it generated
code. I also wanted the initial output to separate functional requirements
from scalability ideas.

### Prompt

```text
You are assisting me as an engineering copilot on a Java backend assignment.

The goal is to build a reviewable URL-shortener prototype and to demonstrate
how I use AI for requirement interpretation, task decomposition,
implementation, debugging, test generation, documentation, and review.

The engineer owns architecture, correctness, maintainability, security,
validation, and production readiness. Do not act as an autonomous project
owner.

Use Java 17, Spring Boot, Maven, Spring Data JPA, JUnit 5, Mockito, and
MockMvc. Start with H2 so the first greenfield version is easy to run.
Do not introduce Redis, PostgreSQL, NGINX, Docker, Kafka, Kubernetes, or a
frontend framework in the first implementation.

Before writing code, produce:
1. a normalized problem statement;
2. functional requirements;
3. non-functional requirements;
4. assumptions and ambiguities;
5. core entities;
6. API contracts and HTTP statuses;
7. a simple greenfield architecture diagram;
8. task dependencies and implementation order;
9. acceptance criteria;
10. a test strategy;
11. risks and future deep-dive topics.

Explain alternatives for important decisions and wait for my approval before
creating files.
```

### My review and decision

I approved a layered Spring Boot design with separate controllers, a service,
a repository, DTO records, domain exceptions, and a code generator. I kept
the first architecture intentionally small. I did not approve distributed
infrastructure at this stage because the core behavior had not yet been
validated.

## 5. Phase 2: Refine the greenfield architecture

### My engineering objective

I wanted the initial design to make the API and data ownership clear before
implementation.

### Prompt

```text
Refine the approved greenfield design without writing code.

Use these initial API contracts:
- POST /api/v1/urls
- GET /{shortCode}
- GET /api/v1/urls/{shortCode}/analytics

Use one ShortUrl entity and do not expose it directly through the API.
Propose DTO names, service responsibilities, repository operations, exception
types, and a control-flow diagram for creation and redirect.

The redirect must use HTTP 302. The original URL must be limited to HTTP and
HTTPS and must not exceed 2048 characters.

For short-code generation, compare random Base62 values with a sequential
counter. Recommend an approach for this prototype, explain how uniqueness
will be enforced, and define a bounded collision policy.

Wait for approval before implementation.
```

### My review and decision

I selected seven-character random Base62 values generated with
`SecureRandom`. I required a maximum of five candidate attempts and a unique
database constraint. I selected DTO records named `CreateShortUrlRequest`,
`CreateShortUrlResponse`, `UrlAnalyticsResponse`, and `ApiErrorResponse`.

I also required an atomic repository update for analytics because a normal
read-modify-write implementation could lose increments under concurrency.

## 6. Phase 3: Implement the greenfield backend

### My engineering objective

I wanted Claude to implement only the approved core and preserve a reviewable
change set.

### Prompt

```text
Implement the approved greenfield URL-shortener backend.

Create or update only the files required for:
- ShortUrl;
- ShortUrlRepository;
- ShortCodeGenerator;
- CreateShortUrlRequest;
- CreateShortUrlResponse;
- UrlAnalyticsResponse;
- ApiErrorResponse;
- UrlShortenerService;
- UrlController;
- RedirectController;
- domain exceptions;
- GlobalExceptionHandler;
- focused unit, controller, and integration tests.

Requirements:
- Java 17 and constructor injection;
- seven-character Base62 codes;
- SecureRandom;
- a maximum of five generation attempts;
- a database unique constraint on shortCode;
- HTTP and HTTPS validation;
- a 2048-character URL limit;
- POST creation returns 201;
- redirect returns 302 with Location;
- unknown or inactive code returns 404;
- analytics returns clickCount and lastAccessedAt;
- analytics increment is atomic.

Show the exact file plan first. Do not modify unrelated files. Do not run Git
commands. After approval, make the changes, run focused tests, run
./mvnw clean test, and report the changed files and results.
```

### Claude's contribution

Claude created the core entity, repository, DTOs, service, controllers,
generator, exception handling, and tests.

### My review and decision

I reviewed the API boundaries, persistence rules, HTTP status mappings, and
analytics update. I accepted the layered structure because controllers
remained thin and `UrlShortenerService` retained the business logic.

## 7. Phase 4: Correct test-toolchain incompatibility

### My engineering objective

The generated tests initially used imports or test dependencies that did not
match the actual Spring Boot version. I wanted a narrow correction rather
than a broad rewrite.

### Prompt

```text
The generated tests do not compile with the Spring Boot version used by this
project.

Read only pom.xml and the affected test classes. Identify the exact version
compatibility issue and correct only the required dependencies and imports.
Preserve all test intent and production code.

Use the Spring Boot test packages that are correct for this project,
including the appropriate MockMvc test annotations, Mockito bean override,
and ObjectMapper package.

Run the failing test first, then run ./mvnw clean test. Do not change
unrelated code or Git history.
```

### My review and decision

I required Claude to align the tests with the actual framework rather than
downgrade the application or remove coverage. The final test source uses the
Spring Boot 4-style test packages, `@MockitoBean`, and
`tools.jackson.databind.ObjectMapper`.

This phase demonstrates that I did not assume AI-generated test code was
correct simply because it looked plausible.

## 8. Phase 5: Add expiration as a brownfield enhancement

### My engineering objective

I wanted to add link lifecycle behavior to an existing API without breaking
clients that did not send expiration.

### Prompt

```text
Add optional URL expiration as a backward-compatible brownfield change.

Before editing, analyze the impact on:
- ShortUrl;
- CreateShortUrlRequest;
- CreateShortUrlResponse;
- UrlAnalyticsResponse;
- UrlShortenerService;
- ShortUrlRepository;
- exception handling;
- unit, controller, and integration tests.

Requirements:
- use Instant;
- expiresAt remains optional;
- a supplied expiresAt must be in the future;
- an expired redirect returns 410 Gone;
- an expired redirect must not increment clickCount;
- analytics for an expired mapping remains available;
- short codes are not recycled;
- existing non-expiring requests continue to work.

Explain the order in which expiration and analytics checks must occur. Wait
for approval, make only the approved changes, and run the full regression
suite.
```

### Claude's contribution

Claude added `expiresAt` to the entity and API records, added
`ShortUrlExpiredException`, updated the repository query, updated the
service, and created expiration tests.

### My review and decision

I required expiration to be checked before a successful analytics update. I
also retained historical analytics and rejected automatic code reuse because
a previously shared short URL should not later represent a different
destination.

## 9. Phase 6: Resolve the ambiguous memorable-URL requirement

### My engineering objective

The phrase “memorable URL” was not specific enough to implement safely. I
wanted Claude to identify the ambiguity before writing code.

### Prompt

```text
The new requirement is: "Users should be able to create memorable URLs."

Do not implement it immediately. First identify the possible meanings and the
product decisions that are missing.

Analyze:
- user-selected alias versus generated dictionary words;
- allowed characters;
- minimum and maximum length;
- case sensitivity;
- normalization;
- duplicate behavior;
- reserved application routes;
- reuse of expired or inactive aliases;
- API and database impact;
- validation and security risks.

Recommend the smallest clear interpretation that fits the existing
POST /api/v1/urls contract. Provide acceptance criteria and wait for my
approval.
```

### My approved interpretation

I defined “memorable URL” as an optional user-selected custom alias. I
approved lowercase normalization, 4 to 30 characters, lowercase letters,
digits, hyphens, and underscores. I required `409 Conflict` for duplicates
and reserved routes, and I prohibited reuse of inactive or expired aliases.

### Implementation prompt

```text
Implement the approved custom-alias behavior.

Use the existing customAlias field in CreateShortUrlRequest and preserve the
generated-code flow when customAlias is absent.

Update UrlShortenerService so it:
- rejects reserved values;
- checks existsByShortCode;
- returns CustomAliasConflictException for duplicates;
- catches a save-time DataIntegrityViolationException for an alias race;
- does not call ShortCodeGenerator when an alias is supplied.

Add controller, service, and integration tests for normalization, valid
redirects, invalid length, invalid characters, duplicates, reserved aliases,
a save-time conflict, and attempted reuse of a deactivated alias.

Do not change the public endpoints or add user accounts.
```

## 10. Phase 7: Add a reviewable browser interface

### My engineering objective

I wanted the evaluator to exercise the prototype without requiring Postman,
but I did not want a frontend framework to distract from backend engineering.

### Prompt

```text
Add a minimal browser interface using only static HTML, CSS, and JavaScript
under src/main/resources/static.

The UI must:
- create a short URL;
- accept optional customAlias;
- accept optional expiration and convert it to UTC ISO-8601;
- display the returned short URL and short code;
- provide copy and open actions;
- look up analytics by short code;
- display API validation and conflict messages;
- call /actuator/health.

Do not add React, Angular, a build tool, or a new API. Do not modify backend
contracts unless a real integration issue is found.
```

### My review and decision

I accepted the static approach because it remained small and directly reused
the backend API. I reviewed the JavaScript to ensure it sent
`originalUrl`, `customAlias`, and `expiresAt` with the names used by
`CreateShortUrlRequest`.

## 11. Phase 8: Correct the static-resource routing defect

### My engineering objective

After the UI was added, static resource requests entered the root redirect
mapping. I wanted to preserve valid short-code redirects without adding
special-case controller methods for each file.

### Prompt

```text
The browser UI exposes a routing conflict. Requests such as /index.html,
styles.css, or app.js are being interpreted as short codes by
RedirectController.

Fix only the route-matching problem. Preserve GET /{shortCode} for valid
codes and aliases. Do not add unrelated route exceptions or change the API
prefix.

Constrain the path variable to the character set already supported by
generated codes and custom aliases. Add regression coverage and run the full
test suite.
```

### My review and decision

I approved the regex-constrained mapping:

```java
@GetMapping("/{shortCode:[a-zA-Z0-9_-]+}")
```

This was a targeted correction based on integration behavior, not a blind
acceptance of the first UI output.

## 12. Phase 9: Perform the scalability and reliability deep dive

### My engineering objective

The working H2 application satisfied the functional requirements, but it did
not yet explain how the system could handle a read-heavy redirect workload or
multiple application instances.

### Prompt

```text
The core application, expiration, aliases, analytics, tests, and UI are
working. Do not add infrastructure yet.

Perform a scalability and reliability gap analysis for the current design.

Analyze:
- the expected read-heavy redirect pattern;
- the existing shortCode database lookup;
- synchronous analytics writes;
- H2 limitations;
- local in-memory cache versus shared Redis;
- PostgreSQL versus a distributed NoSQL database;
- one application instance versus multiple stateless instances;
- load balancing;
- cache hit, cache miss, stale cache, Redis failure, and expiration behavior.

Recommend the smallest justified enhancement for this assignment. Separate
what should be implemented from what should remain a future production
option. Wait for my approval.
```

### My review and decision

I decided that PostgreSQL, Redis, two application instances, NGINX, and
Docker Compose were justified for a scalable demonstration. I rejected
adding DynamoDB, Cassandra, Kafka, Kubernetes, or a CDN because the prototype
did not require that operational complexity.

## 13. Phase 10: Add the PostgreSQL scalable profile

### My engineering objective

I wanted shared durable storage for multiple instances without removing the
simple local profile.

### Prompt

```text
Add a scalable Spring profile that uses PostgreSQL while preserving the
default H2 behavior.

Update only the build and configuration files required for PostgreSQL.

Use environment variables:
- DB_URL;
- DB_USERNAME;
- DB_PASSWORD;
- APP_BASE_URL;
- INSTANCE_NAME.

Keep the public API unchanged. Keep the local profile runnable without
PostgreSQL. Set spring.jpa.hibernate.ddl-auto=update only for the scalable
demonstration. Explain any production limitation of schema auto-update.

Run the full test suite after the configuration change.
```

### My review and decision

I approved profile separation because it prevented distributed
infrastructure from becoming a requirement for every developer run.
PostgreSQL became the shared source of truth for the scalable environment.

## 14. Phase 11: Add optional Redis cache-aside

### My engineering objective

I wanted to reduce repeated redirect lookup work without allowing Redis to
become authoritative or mandatory.

### Prompt

```text
Add an optional redirect cache through a small RedirectCache abstraction.

Implement:
- RedirectCache;
- NoOpRedirectCache when app.cache.enabled is false or absent;
- RedisRedirectCache when app.cache.enabled is true.

Requirements:
- use key prefix redirect:;
- cache only the original URL mapping;
- PostgreSQL remains the source of truth;
- Redis read failure becomes a cache miss;
- Redis write or eviction failure is logged but does not fail the request;
- the cache TTL is the smaller of app.cache.redirect-ttl and the remaining
  time before expiresAt;
- a cache hit still performs ShortUrlRepository.incrementClickCount;
- if the cache-hit update affects zero rows, evict the cache and reload the
  database state;
- do not cache inactive or expired mappings.

Update UrlShortenerService and add focused tests for cache hit, cache miss,
stale inactive data, stale expired data, analytics updates, and eviction.

Do not change controller contracts.
```

### My review and decision

I approved the abstraction because it kept local mode simple and made the
fallback behavior explicit. I specifically required the database analytics
update on a cache hit so the performance enhancement did not silently break
business correctness.

## 15. Phase 12: Add multiple instances and request identity

### My engineering objective

I needed evidence that two application instances could share state and serve
the same short codes.

### Prompt

```text
Prepare the application for two stateless instances in the scalable
environment.

Add InstanceHeaderFilter so every response includes:
X-App-Instance: ${app.instance-name}

The local default must remain local-instance. The two scalable instances
will use app1 and app2.

Do not change response DTOs. Add a focused filter test. Explain how the
header will be used to validate routing and shared persistence.
```

### My review and decision

I approved a response header instead of modifying every DTO because it
applied consistently to creation, redirect, analytics, health, and error
responses.

## 16. Phase 13: Add NGINX and Docker Compose

### My engineering objective

I wanted a repeatable five-service environment with one public endpoint.

### Prompt

```text
Create the scalable Docker demonstration.

Add:
- a multi-stage Dockerfile for the Spring Boot application;
- .dockerignore;
- .env.example;
- docker-compose.yml;
- infra/nginx/nginx.conf.

The Compose environment must contain:
- postgres;
- redis;
- app1;
- app2;
- nginx.

Requirements:
- NGINX is the public endpoint on localhost:8080;
- app1 and app2 use the scalable profile;
- both application instances share PostgreSQL and Redis;
- app1 uses INSTANCE_NAME=app1;
- app2 uses INSTANCE_NAME=app2;
- APP_BASE_URL is http://localhost:8080;
- expose direct diagnostic ports for app1 and app2;
- add service dependencies and health checks where practical;
- keep .env out of Git and commit .env.example;
- do not claim production high availability.

Before creating files, show the service topology and environment variables.
After approval, validate docker compose config and report the startup
commands.
```

### My review and operational ownership

I manually started Docker Desktop, corrected environment-variable issues,
resolved the port conflict, verified that `.env` was ignored, and reviewed
`git status` before committing. I did not delegate Git history or secret
handling to Claude.

## 17. Phase 14: Validate cross-instance behavior

### My engineering objective

I needed evidence that the scalable topology was more than a diagram.

### Prompt

```text
Provide a minimal final validation plan for the running Docker environment.

The plan must verify:
1. all five services are running;
2. repeated health requests show app1 and app2;
3. URL creation through NGINX returns 201;
4. the resulting short code redirects through NGINX with 302;
5. creation and redirect can be served by different application instances;
6. analytics returns 200;
7. Redis can be inspected;
8. Redis fallback and single-instance failover are recorded only if those
   tests are actually run.

Use curl and docker compose commands. Do not invent metrics or successful
results.
```

### Recorded evidence

The final recorded creation request returned `201 Created` and
`X-App-Instance: app1`. The redirect for the returned code returned
`302 Found`, `X-App-Instance: app2`, and the expected `Location` header.

I treated this as evidence of request distribution and shared persistence. I
did not treat it as evidence of a production availability target or load
capacity.

## 18. Phase 15: Final documentation and review

### My engineering objective

I wanted the final documentation to explain the engineering sequence rather
than repeat the final architecture in several files.

### Prompt

```text
Review the final code and update the documentation so that every class name,
DTO name, endpoint, property, test claim, and architecture statement matches
the repository.

Consolidate the documentation into:
1. engineering design and architecture;
2. AI prompt and decision log;
3. scenarios, validation, and risks;
4. setup instructions;
5. final engineering summary.

The documents must show:
- requirement understanding;
- task decomposition;
- greenfield implementation;
- brownfield expiration;
- ambiguity resolution for custom aliases;
- deep-dive reasoning for PostgreSQL, Redis, multiple instances, NGINX, and
  Docker;
- Claude's contribution;
- engineer review, corrections, and rejected directions;
- model selection and token management;
- tests and manual evidence;
- limitations and production gaps.

Use complete sentences. Do not claim unmeasured performance, availability,
or failover. Do not create duplicate prompt files.
```

## 18a. Phase 16: Capture detailed click events for a future reporting requirement

### My engineering objective

A new requirement asked for "click reporting" without saying which
dimensions a report would need. I did not want Claude to design a reporting
API against an undefined requirement, so I first worked through what data
would have to exist before any report could be built.

### Ambiguous reporting requirement

The requirement, as given, was: "We need reporting on link clicks." It did
not say whether reporting meant a per-link total (which already existed as
`clickCount`), a time series, a breakdown by device, or an
exportable log. I treated this as an ambiguous requirement rather than
implementing a guess.

### Clarification of required data

Before approving any implementation, I decided that any plausible reporting
direction — trends over time, geographic breakdown, or browser/device
breakdown — would require at least one row per successful redirect
containing when it happened, where it came from, and what accessed it. I
defined the minimum data set as `shortCode`, `clickedAt`, `country`, and
`browser`.

### Review of the existing analytics implementation

I reviewed `UrlShortenerService.resolveOriginalUrl` and
`ShortUrlRepository.incrementClickCount` before deciding anything. The
existing analytics were a single atomic conditional update that increments
`clickCount` and sets `lastAccessedAt` on `ShortUrl` (section 10 of
`01-engineering-design-and-architecture.md`). This update already runs once
per successful redirect and is the source of truth for the existing
`GET /api/v1/urls/{shortCode}/analytics` endpoint.

### Decision to retain synchronous aggregate analytics

I decided not to touch this existing update. It is small, already atomic,
already tested, and already correct under concurrency. Changing it to
support a new, still-undefined reporting requirement would have risked the
one piece of analytics behavior that was already validated.

### Decision to store detailed events asynchronously

I decided that per-click detail belongs in a new, separate table
(`click_events`, entity `ClickEvent`) written by a new, separate
asynchronous path, rather than by extending `ShortUrl` or by making the
existing synchronous update do more work. This kept the redirect's
critical path — resolve the mapping, update the aggregate, return
`302 Found` — exactly as fast and as reliable as before.

### Rejection of Kafka for this prototype

I considered a message broker (Kafka or a similar durable queue) for
delivering click events, and rejected it for this stage. There is no
reporting consumer to feed yet, and the reporting API itself is not built.
Adding a broker, topics, and a consumer group would be operational
complexity without a corresponding requirement. I recorded Kafka or a
comparable durable queue as a future option rather than a current
implementation detail.

### Prompt

```text
A new requirement asks for click reporting, but it does not define which
dimensions a report needs. Do not implement a reporting API yet.

First determine the minimum detailed data that any plausible report would
need, and review the existing clickCount/lastAccessedAt analytics update
before proposing a change.

Then design and implement only the capture of that detailed data:
- a new ClickEvent entity/table, separate from ShortUrl;
- shortCode, clickedAt, country (from the CF-IPCountry header, "Unknown" if
  missing), and browser (parsed from User-Agent);
- persistence must be asynchronous and must not use Kafka or another
  message broker;
- the asynchronous method must live on its own Spring bean, not on
  RedirectController or UrlShortenerService;
- do not pass HttpServletRequest across the asynchronous boundary — extract
  header values on the request thread first;
- a failure to persist a click event must never break or delay a
  successful redirect;
- unknown, inactive, expired, and otherwise failed redirects must not
  create a click event;
- the existing synchronous clickCount and lastAccessedAt behavior on
  ShortUrl must not change.

Show the file plan first. Add focused unit and controller tests. Do not
implement reporting endpoints. Run the full test suite and report results.
```

### Claude's contribution

Claude proposed the `ClickEvent` entity, `ClickEventRepository`,
`ClickEventRecorder` (an `@Async` `@Transactional` method on its own
`@Service` bean), `BrowserDetector`, and `AsyncConfig` (`@EnableAsync`). It
updated `RedirectController` to read the `CF-IPCountry` and `User-Agent`
headers and call `ClickEventRecorder.recordClickEvent` after a successful
`resolveOriginalUrl`, and added `RedirectControllerTest`,
`ClickEventRecorderTest`, and `BrowserDetectorTest`.

### Engineer review of country headers, browser detection, transaction boundaries, and failure handling

- **Country headers**: I required `CF-IPCountry` to default to `"Unknown"`
  rather than `null` or an empty string, and I flagged in
  `03-scenarios-validation-and-risks.md` that the header must not be
  trusted unless the deployment sits behind a proxy that sets it
  authoritatively — the Docker Compose NGINX configuration in this
  repository does not set or strip it, so it is currently client-supplied.
- **Browser detection**: I reviewed `BrowserDetector` and confirmed the
  Chromium-based-browser ordering (Edge and Opera checked before Chrome,
  since their user agents also contain `Chrome/`) and that it returns
  `"Unknown"` for anything unrecognized instead of guessing.
- **Transaction boundaries**: I confirmed `recordClickEvent` is
  `@Transactional` on its own, independent of the transaction (if any)
  around `resolveOriginalUrl`, so a click-event persistence failure cannot
  roll back the already-committed `clickCount`/`lastAccessedAt` update.
- **Failure handling**: I required the explicit `try`/`catch` inside
  `recordClickEvent` so that a repository failure is logged and swallowed
  rather than left to rely only on default async-exception logging, and I
  verified `RedirectControllerTest` proves no click event is recorded for
  unknown or expired short codes.

### Implementation and validation

I ran `./mvnw clean test` after implementation and manually exercised a
redirect against the PostgreSQL-backed `scalable` profile to confirm a row
appeared in `click_events` with the expected `short_code`, `country`, and
`browser`, and that the existing `clickCount`/`lastAccessedAt` analytics
were unaffected. This evidence is recorded in section 6 of
`03-scenarios-validation-and-risks.md`, clearly separated from the
automated test suite.

## 19. AI contribution, engineer review, and final ownership

| Area | Claude accelerated | I reviewed or decided |
|---|---|---|
| Requirement analysis | Claude organized the initial scope and open questions. | I selected the actual scope and deferred unnecessary infrastructure. |
| Core code | Claude generated the first implementation and tests. | I approved names, boundaries, validation, status codes, and atomic analytics. |
| Test compatibility | Claude proposed the framework correction. | I required compatibility with the actual Spring Boot version and regression tests. |
| Expiration | Claude implemented the affected code. | I required backward compatibility, `410 Gone`, and no analytics increment after expiry. |
| Custom aliases | Claude implemented validation and conflict handling. | I defined the product meaning, normalization, reserved routes, and no-reuse policy. |
| UI | Claude created static assets. | I limited the solution to plain HTML, CSS, and JavaScript. |
| Routing defect | Claude applied the regex correction. | I identified the integration issue and constrained the fix. |
| Redis | Claude created the cache classes and tests. | I required PostgreSQL authority, optional caching, stale eviction, and analytics correctness. |
| Deployment | Claude drafted Docker and NGINX assets. | I handled environment, secrets, ports, Git, and final execution. |
| Documentation | Claude helped structure and edit the files. | I required code reconciliation, complete sentences, evidence limits, and a clear engineering narrative. |

The final engineering decisions and acceptance of the system remained my
responsibility.
