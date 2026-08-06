# AI Prompt and Engineering Decision Log

## 1. Purpose

Claude Code was used as an engineering assistant throughout the URL-shortener
project. Its role was to accelerate requirement analysis, implementation,
testing, debugging, infrastructure setup, and documentation.

The project was not delegated to AI. Architecture, scope, validation, security,
risk acceptance, Git history, testing, and final approval remained under direct
engineering ownership.

## 2. Working Method

The project followed this review-controlled process:

```text
Requirement
   ↓
Clarification and decomposition
   ↓
Prompt to Claude Code
   ↓
Proposed design or implementation
   ↓
Engineering review
   ↓
Testing and correction
   ↓
Final acceptance
```

The work was divided into small phases. Prompts were limited to one feature or
problem at a time, unrelated refactoring was prohibited, and test results were
required before changes were accepted.

Git commands, Docker execution, secret handling, and final commits were managed
manually.

## 3. Greenfield Design

The first phase focused only on the core URL-shortening behavior.

### Representative prompt

```text
Analyze the URL-shortener assignment before writing code.

Use Java 17, Spring Boot, Maven, Spring Data JPA, JUnit, Mockito,
MockMvc, and H2.

Provide:
- requirements;
- assumptions and ambiguities;
- entity and API design;
- implementation order;
- acceptance criteria;
- test strategy;
- risks.

Do not introduce Redis, PostgreSQL, Docker, NGINX, Kafka, or Kubernetes.
Wait for approval before implementation.
```

### Decision

A layered Spring Boot design was approved with controllers, a service,
repositories, DTOs, domain exceptions, global exception handling, and a
short-code generator.

Distributed infrastructure was intentionally deferred until the core create,
redirect, and analytics flow was working.

## 4. Core Backend

Claude generated the initial entity, repository, DTOs, service, controllers,
exception handling, and tests.

The implementation was reviewed for:

- API boundaries
- Validation
- HTTP status codes
- Collision handling
- Database constraints
- Analytics correctness
- Test coverage

The approved design kept controllers thin and retained business logic in
`UrlShortenerService`.

## 5. Base62 Decision

Base62 was selected instead of UUIDs because the public URL needed to remain
compact.

The final design uses:

- Seven Base62 characters
- `SecureRandom`
- Maximum five attempts
- Application-level collision checks
- A database unique constraint

The internal database identifier remains `Long`; Base62 is used only for the
public short code.

## 6. Brownfield Expiration

Expiration was added after the original API was stable.

The approved behavior was:

- `expiresAt` remains optional
- `Instant` is used for UTC-safe handling
- Past values are rejected
- Expired redirects return `410 Gone`
- Expired redirects do not increment analytics
- Historical analytics remain available
- Expired codes are not reused

This preserved backward compatibility for clients that did not send an
expiration value.

## 7. Ambiguous Custom-Alias Requirement

The phrase “memorable URL” was treated as an ambiguous requirement rather than
implemented immediately.

The final interpretation was an optional user-selected alias with:

- Lowercase normalization
- Length from 4 to 30 characters
- Letters, digits, hyphens, and underscores only
- `409 Conflict` for duplicates
- `409 Conflict` for reserved routes
- No reuse of inactive or expired aliases

This interpretation was selected because it was clear, testable, and compatible
with the existing API.

## 8. Browser Interface and Routing Fix

A small static UI was added using HTML, CSS, and JavaScript. A frontend
framework was rejected because it was unnecessary for the backend assignment.

During integration review, static resources were found to be entering the
redirect route.

The final correction was:

```java
@GetMapping("/{shortCode:[a-zA-Z0-9_-]+}")
```

This preserved valid redirects while preventing static files from being treated
as short codes.

## 9. Scalability Decisions

After the core application was stable, the following limitations were reviewed:

- H2 state was local to one process
- Multiple instances could not share mappings
- Redirects repeatedly queried the database
- No shared cache existed
- No single entry point existed for multiple instances

The following were approved:

- PostgreSQL
- Optional Redis cache-aside
- Two Spring Boot instances
- NGINX
- Docker Compose

Kafka, Kubernetes, Cassandra, DynamoDB, CDN integration, and multi-region
replication were rejected or deferred because they were not justified by the
prototype requirements.

## 10. PostgreSQL, Redis, and Docker

H2 remained the default local profile. PostgreSQL was added for the scalable
profile so both application instances could share durable state.

Redis was kept optional with these rules:

- PostgreSQL remains the source of truth
- Redis failures become cache misses
- Redis write failures do not fail redirects
- Cache TTL respects expiration
- Cache hits still update analytics
- Expired or inactive mappings are not served from cache

The Docker topology contains PostgreSQL, Redis, app1, app2, and NGINX.

Cross-instance validation confirmed that a URL created through one application
instance could be resolved through the other. This demonstrates shared state
and request distribution, not production high availability.

## 11. Detailed Click Events

The existing synchronous `clickCount` and `lastAccessedAt` behavior was
preserved.

A separate asynchronous path was added for detailed events with:

- `shortCode`
- `clickedAt`
- Browser derived from `User-Agent`

The asynchronous method runs in a separate Spring bean. `HttpServletRequest` is
not passed to the background thread, and event-save failures do not fail valid
redirects.

The current implementation does not store country information.

This design accepts eventual consistency for detailed events while preserving
redirect reliability.

## 12. Security Validation

Focused tests were added for:

- SQL-injection-style input
- XSS-style input
- Unsafe URL schemes
- Invalid aliases
- Oversized URLs
- Sanitized error responses

Authentication and rate limiting remain outside scope.

One test intentionally triggers an exception containing SQL-like text to verify
that internal details are not returned to the client. The server logs the
exception, while the API response remains sanitized.

Recorded result:

```text
Tests run: 71
Failures: 0
Errors: 0
BUILD SUCCESS
```

## 13. Performance Testing

k6 scripts were added for:

- Smoke testing
- Redirect-heavy traffic
- Lower-rate URL creation
- Analytics requests
- Configurable virtual users
- p50, p95, and p99 latency
- Error-rate thresholds
- Staged execution up to 1,000 virtual users

No claim of 1,000-user support is made until the complete test runs
successfully and all thresholds pass.

## 14. Corrections Made During Review

| Issue | Correction |
|---|---|
| Generated tests did not match the framework version | Imports and annotations were aligned with the actual Spring Boot version |
| Static resources entered the redirect route | The route was restricted with a regular expression |
| Expiration could affect analytics correctness | Expiration checks were required before successful counting |
| Redis risked becoming authoritative | PostgreSQL remained the source of truth |
| Detailed events could affect redirect reliability | Event persistence was isolated through `@Async` |
| Country analytics was no longer required | The field was removed from code and documentation |
| Performance claims were not proven | Claims were deferred until measured results exist |
| Documentation became repetitive | Duplicate explanations were consolidated |

These corrections demonstrate that AI output was reviewed and modified rather
than accepted automatically.

## 15. AI Contribution and Ownership

| Area | Claude Code supported | Final responsibility |
|---|---|---|
| Requirements | Organized scope and open questions | Scope and acceptance criteria |
| Backend | Generated initial code and tests | Architecture and API behavior |
| Expiration | Updated affected layers | Backward compatibility |
| Aliases | Implemented validation | Product rules |
| Redis | Generated cache implementation | Source-of-truth and fallback decisions |
| Docker | Drafted Docker and NGINX assets | Environment, secrets, ports, execution |
| Security | Added focused tests | Security scope and acceptance |
| Performance | Added k6 scripts | Thresholds and performance claims |
| Documentation | Drafted initial content | Accuracy, concision, and approval |

## 16. Final Ownership Statement

Claude Code accelerated analysis, implementation, testing, debugging,
infrastructure work, and documentation.

Final responsibility for architecture, scope, correctness, risk acceptance,
security, testing, Docker validation, Git history, secret handling, and project
acceptance remained with me.

The generated output was reviewed, corrected where necessary, validated through
tests, and accepted only after it matched the project requirements.
