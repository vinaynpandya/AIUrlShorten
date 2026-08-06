# Final Summary

## 1. Executive Summary

This project is a Java 17 / Spring Boot URL-shortener prototype that evolved
from a single-instance H2 greenfield service into a PostgreSQL- and
Redis-backed, NGINX-fronted, two-instance deployment, covering URL creation,
redirects, optional expiration and custom aliases, aggregate and
asynchronous detailed click analytics, and a soft-delete endpoint. Claude
Code was used throughout as an engineering accelerator — for requirement
analysis, file planning, implementation, test generation, security and
performance test scaffolding, and documentation drafting — with every
design decision, generated file, and test result reviewed and approved by
the engineer before acceptance (`docs/02-ai-prompt-and-decision-log.md`).
Authentication, authorization, rate limiting, multi-region deployment, and a
completed 1,000-concurrent-user performance benchmark remain out of scope.
Git commands, Docker execution, and secret handling were performed manually
throughout, not delegated to AI.

## 2. Risks, Trade-Offs, and Assumptions

**Assumptions.** The project was scoped as a bounded prototype, not a
production system — architecture, scope, validation, security, risk
acceptance, Git history, and final approval stayed under direct engineering
ownership throughout (`docs/02` §1). Kafka, Kubernetes, Cassandra, DynamoDB,
CDN integration, and multi-region replication were assumed out of scope
because they were not justified by the prototype's actual requirements
(`docs/02` §9).

**Trade-offs** (`docs/01` §9):
- Random Base62 codes keep public URLs compact and non-sequential, at the
  cost of needing collision handling.
- PostgreSQL provides transactions and a uniqueness guarantee, at the cost
  of external infrastructure in the scalable profile.
- Redis cache-aside reduces repeated database lookups, at the cost of added
  cache complexity.
- Synchronous aggregate analytics keep click counts immediately consistent,
  at the cost of one database write per redirect.
- Asynchronous detailed click events keep persistence off the redirect
  request thread, at the cost of eventual consistency and possible event
  loss if the process stops mid-flight.
- Two application instances behind NGINX demonstrate horizontal execution,
  without providing complete high availability.

**Technical risks** (`docs/01` §10):
- The generated-code path checks `existsByShortCode` before saving but does
  not retry on a save-time uniqueness race.
- Detailed click events are not durably queued — a process crash between
  capture and persistence can lose an event without affecting the redirect
  itself.
- Redis hit ratio has not been measured.
- H2, used for local development and tests, does not reproduce every
  PostgreSQL-specific behavior.

## 3. Quality Gates

- **Automated tests:** 76 passing tests (unit, controller, and integration)
  across 11 test classes — `./mvnw clean test` → `BUILD SUCCESS`, 0
  failures, 0 errors.
- **Security tests:** a dedicated suite (`SecurityValidationTest`) covering
  SQL-injection-style input and XSS-style input, part of the 76 above
  (`docs/02` §12).
- **Performance tests:** k6 assets at `performance/k6/url-shortener-load.js`
  covering smoke, redirect-heavy, creation, and analytics workloads with
  configurable virtual users and staged execution up to 1,000 VUs. No claim
  of 1,000-concurrent-user support is made — that requires the full staged
  run to complete successfully with all thresholds passing, which has not
  been done (`docs/02` §13).
- **Static analysis:** `maven-checkstyle-plugin` and `spotbugs-maven-plugin`
  were added to `pom.xml`, both report-only (`failOnViolation=false` /
  `failOnError=false` — they do not fail the build). Baseline counts from
  this session's console output: **358 Checkstyle violations** (default Sun
  Checks ruleset) and **6 SpotBugs findings** (all Medium severity, all
  `EI_EXPOSE_REP` / `EI_EXPOSE_REP2` — mutable-field exposure patterns).
  Neither baseline has been remediated.

## 4. Secure AI Usage

No real credentials, secrets, or proprietary data were included in any
prompt to Claude Code throughout this project. AI-generated Docker and
NGINX configuration was reviewed before use rather than applied directly.
Git commands, Docker execution, and secret handling (including keeping
`.env` out of version control) were performed manually by the engineer
rather than delegated to AI at any point.

## 5. Limitations

Reused verbatim from README.md's "Current Scope and Limitations":

- No authentication, authorization, or link ownership
- No rate limiting or abuse prevention
- No update or delete APIs
- No custom domains
- No cleanup scheduler
- No durable analytics queue
- No Flyway or Liquibase migrations
- No replicated or multi-region infrastructure
- No completed 1,000-user benchmark result

> Note: "No update or delete APIs" predates the soft-delete `DELETE
> /api/v1/urls/{shortCode}` endpoint added this session (see
> `documents/03-scenarios-and-validation.md`, Scenario 3). README.md has not
> been updated to reflect this yet.

## 6. Final Deliverables Checklist

- [x] Runnable prototype — `docs/04-setup-instructions.md`; source under
      `src/main/java/com/assignment/urlshortener/`
- [x] Architecture overview — `docs/01-engineering-design-and-architecture.md`
- [x] Greenfield scenario — `documents/03-scenarios-and-validation.md`,
      Scenario 1
- [x] Brownfield scenario — expiration —
      `documents/03-scenarios-and-validation.md`, Scenario 2
- [x] Brownfield scenario — soft-delete endpoint —
      `documents/03-scenarios-and-validation.md`, Scenario 3
- [x] Ambiguous requirement scenario — custom alias —
      `documents/03-scenarios-and-validation.md`, Scenario 4
- [x] Setup instructions — `docs/04-setup-instructions.md`
- [x] Automated tests — 76 tests passing, `./mvnw clean test`
- [x] Static analysis — Checkstyle + SpotBugs wired into `pom.xml`,
      report-only (baseline not yet remediated — see §3 above)
- [x] AI-assisted engineering documentation —
      `docs/02-ai-prompt-and-decision-log.md`; `documents/00-assessor-guide.md`
      Section B
