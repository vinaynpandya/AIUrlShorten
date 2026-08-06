# Project Guide Document

This file is the entry point for evaluating this project. It maps each
expected deliverable to where it actually lives in this repository. It adds
no new claims — every "Where to look" cell points at a file/section that
already exists in `documents/`, `docs/`, or the codebase.

## Section A — Deliverables & Evaluation Map

| Requirement | Where to look
|---|---|---|
| Working prototype | `documents/04-setup-instructions.md` (all three run options, identical copy of `docs/04-setup-instructions.md`); source under `src/main/java/com/assignment/urlshortener/`
| Architecture overview | `documents/01-architecture-and-design.md` (reformatted from `docs/01-engineering-design-and-architecture.md` — component diagram §3, data model §4, API design §5, request flow §7)
| Greenfield scenario | `documents/03-scenarios-and-validation.md`, Scenario 1 (reformatted from `docs/03-scenarios-validation-and-risks.md` §2)
| Brownfield scenario — expiration | `documents/03-scenarios-and-validation.md`, Scenario 2; `documents/01-architecture-and-design.md` §6 "Expiration" 
| Brownfield scenario — soft-delete endpoint | `documents/03-scenarios-and-validation.md`, Scenario 3 | Documented |
| Ambiguous requirement scenario | `documents/03-scenarios-and-validation.md`, Scenario 4; `documents/02-ai-engineering-log.md` §5 "Decision Log" (row "Custom alias") 
| Setup instructions | `documents/04-setup-instructions.md` (full file, identical copy of `docs/04-setup-instructions.md`) 
| Testing, limitations, trade-offs | `documents/05-final-summary.md` §2 (Risks, Trade-Offs, and Assumptions), §3 (Quality Gates), §5 (Limitations); the per-class test-count breakdown table remains only in `docs/03-scenarios-validation-and-risks.md` §6 
| Requirement understanding & task decomposition | `documents/01-architecture-and-design.md` §1–2 (overview, scope); `documents/02-ai-engineering-log.md` (full file); `documents/03-scenarios-and-validation.md` (Decomposition subsection per scenario) 
| AI-assisted execution with traceability | `documents/02-ai-engineering-log.md` (full file — representative prompt plus decision log for every phase) 
| Quality gate — automated tests | `documents/05-final-summary.md` §3 "Quality Gates" (current count: 76 tests across 11 classes); per-class breakdown table in `docs/03-scenarios-validation-and-risks.md` §6; run `./mvnw clean test` 
| Quality gate — security tests | `documents/02-ai-engineering-log.md` §5 "Decision Log" (row "Security validation"); `documents/05-final-summary.md` §3; `src/test/java/.../controller/SecurityValidationTest.java` 
| Quality gate — k6 performance | `documents/02-ai-engineering-log.md` §5 "Decision Log" (row "Performance testing"); `documents/05-final-summary.md` §3; `performance/k6/url-shortener-load.js` and `performance/k6/README.md` 
| Quality gate — Checkstyle/SpotBugs static analysis | `documents/05-final-summary.md` §3 "Quality Gates" (358 Checkstyle violations, 6 SpotBugs findings, both report-only); `pom.xml`; run `./mvnw checkstyle:check spotbugs:check` 
| Secure AI usage | `documents/05-final-summary.md` §4 "Secure AI Usage"; `documents/02-ai-engineering-log.md` §2 "Working Method" | Documented |
| Human sign-off on high-impact changes | `documents/02-ai-engineering-log.md` §7 "Corrections Made During Review", §8 "AI Contribution and Ownership", §9 "Final Ownership Statement" 
| Final engineering summary | `documents/05-final-summary.md` (full file) 

## Section B — AI Usage by Phase

Sourced from `docs/02-ai-prompt-and-decision-log.md`.

| Phase | How AI Was Used | My Role |
|---|---|---|
| Requirements | Created prompts to analyze the assignment. Prepare and list requirements, assumptions, and ambiguities before any code was written (§3 "Greenfield Design"); organized scope and open questions (§15 table) | I worked on clarifying the ambiguous requirements such as "memorable URL" (§7); decomposed work into small phases and approved scope before implementation (§1 "Working Method") |
| Design | Proposed the layered design — controllers, service, repositories, DTOs, exceptions (§4); proposed scalability options — PostgreSQL, Redis, NGINX (§9) | Approved the layered design; decided what to defer (Kafka, Kubernetes, Cassandra, DynamoDB, CDN, multi-region — §9); selected API contracts and HTTP statuses |
| Implementation | Generated the initial entity, repository, DTOs, service, controllers, exception handling (§4); the Base62 generator (§5); expiration changes (§6); custom-alias logic (§7); the static UI and route fix (§8); scalable infrastructure (§10); async click events (§11) | Reviewed for API boundaries, validation, HTTP status codes, collision handling, DB constraints (§4); corrected the static-route conflict (§8); enforced PostgreSQL as source of truth with Redis optional (§10); required `@Async` isolation for click events (§11) |
| Testing | Added focused security tests — SQL-injection-style, XSS-style, unsafe URL schemes (§12); added k6 performance scripts (§13) | Required test results before acceptance (§1); verified test suite passed (§12); required no 1,000-user claim without a full passing staged run (§13) |
| Documentation | Drafted initial documentation content (§15 table, row "Documentation") | Owned accuracy, concision, and final approval of all docs (§15); consolidated duplicate explanations (§14 "Documentation became repetitive") |
