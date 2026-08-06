# AI Engineering Log

Reformatted from `docs/02-ai-prompt-and-decision-log.md`. The two sections
that were already tabular there ("Corrections Made During Review", "AI
Contribution and Ownership") are carried over unchanged. The representative
prompt in "Greenfield Design" is kept verbatim. The eight per-decision
narrative sections are reformatted into a consistent
Decision | Options Considered | AI Contribution | Engineer's Final Call |
Rationale table. Where docs/02 does not explicitly state alternatives
considered or an AI/engineer split for a given decision, that is noted as
such rather than invented.

## 1. Purpose

Claude Code was used as an engineering assistant throughout the URL-shortener
project. Its role was to accelerate requirement analysis, implementation,
testing, debugging, infrastructure setup, and documentation.

The project was not delegated to AI. Architecture, scope, validation,
security, risk acceptance, Git history, testing, and final approval remained
under direct engineering ownership.

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

The work was divided into small phases. Prompts were limited to one feature
or problem at a time, unrelated refactoring was prohibited, and test results
were required before changes were accepted.

Git commands, Docker execution, secret handling, and final commits were
managed manually.

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

Distributed infrastructure was intentionally deferred until the core
create, redirect, and analytics flow was working.

## 4. Core Backend

Claude generated the initial entity, repository, DTOs, service, controllers,
exception handling, and tests.

The implementation was reviewed for API boundaries, validation, HTTP status
codes, collision handling, database constraints, analytics correctness, and
test coverage.

The approved design kept controllers thin and retained business logic in
`UrlShortenerService`.

## 5. Decision Log

| Decision | Options Considered | AI Contribution | Engineer's Final Call | Rationale |
|---|---|---|---|---|
| Base62 short-code generation | Base62 vs. UUIDs | Generated the initial backend implementation and tests | Seven Base62 characters; `SecureRandom`; maximum five attempts; application-level collision checks; a database unique constraint; internal ID stays `Long` | The public URL needed to remain compact |
| Expiration (brownfield) | Not explicitly contrasted in docs/02 — behavior was specified directly | Updated the affected layers | `expiresAt` optional; `Instant` for UTC-safe handling; past values rejected; expired redirects return `410 Gone` and don't increment analytics; historical analytics remain available; expired codes not reused | Preserved backward compatibility for clients that did not send an expiration value |
| Custom alias (ambiguous "memorable URL" requirement) | Implement a guess immediately vs. treat the phrase as an ambiguous requirement and clarify before implementing | Implemented the validation | Optional user-selected alias; lowercase normalization; 4–30 characters; letters/digits/hyphens/underscores only; `409 Conflict` for duplicates and reserved routes; no reuse of inactive/expired aliases | Selected because it was clear, testable, and compatible with the existing API |
| Scalability | Approved: PostgreSQL, optional Redis cache-aside, two Spring Boot instances, NGINX, Docker Compose. Rejected/deferred: Kafka, Kubernetes, Cassandra, DynamoDB, CDN integration, multi-region replication | Drafted the Docker and NGINX assets | Approved the five-item list; rejected/deferred the six-item list | Addressed reviewed limitations of a single H2 instance (no shared mappings across instances, repeated DB queries on redirect, no shared cache, no single entry point); rejected items were not justified by the prototype requirements |
| Redis | Redis as optional cache-aside vs. as an authoritative/mandatory store | Generated the cache implementation | PostgreSQL remains the source of truth; Redis failures become cache misses; Redis write failures do not fail redirects; cache TTL respects expiration; cache hits still update analytics; expired/inactive mappings are not served from cache | Keeps PostgreSQL authoritative so Redis failures degrade to cache misses rather than a data-integrity risk |
| Detailed click events | Not explicitly contrasted in docs/02 — the decision is stated directly: preserve the existing synchronous update and add a separate asynchronous path for detailed events | Proposed and implemented the asynchronous capture design (per the Working Method, §2) | Existing synchronous `clickCount`/`lastAccessedAt` behavior preserved; new async path runs in a separate Spring bean; `HttpServletRequest` not passed to the background thread; event-save failures do not fail valid redirects; no country information stored | Accepts eventual consistency for detailed events while preserving redirect reliability |
| Security validation | Not framed as alternatives in docs/02 — scope was defined directly, with authentication and rate limiting explicitly kept outside scope | Added the focused tests | Tests for SQL-injection-style input, XSS-style input, unsafe URL schemes, invalid aliases, oversized URLs, and sanitized error responses; one test intentionally triggers an exception containing SQL-like text to verify internal details are not returned to the client | Confirms the server logs exceptions internally while the API response stays sanitized; recorded result was 71 tests, 0 failures, 0 errors, BUILD SUCCESS |
| Performance testing | Not framed as alternatives in docs/02 — scope was defined directly | Added the k6 scripts | Smoke testing, redirect-heavy traffic, lower-rate URL creation, analytics requests, configurable virtual users, p50/p95/p99 latency, error-rate thresholds, staged execution up to 1,000 virtual users | No claim of 1,000-user support is made until the complete test runs successfully and all thresholds pass |

## 6. Browser Interface and Routing Fix

A small static UI was added using HTML, CSS, and JavaScript. A frontend
framework was rejected because it was unnecessary for the backend
assignment.

During integration review, static resources were found to be entering the
redirect route.

The final correction was:

```java
@GetMapping("/{shortCode:[a-zA-Z0-9_-]+}")
```

This preserved valid redirects while preventing static files from being
treated as short codes.

## 7. Corrections Made During Review

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

These corrections demonstrate that AI output was reviewed and modified
rather than accepted automatically.

## 8. AI Contribution and Ownership

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

## 9. Final Ownership Statement

Claude Code accelerated analysis, implementation, testing, debugging,
infrastructure work, and documentation.

Final responsibility for architecture, scope, correctness, risk acceptance,
security, testing, Docker validation, Git history, secret handling, and
project acceptance remained with me.

The generated output was reviewed, corrected where necessary, validated
through tests, and accepted only after it matched the project requirements.
