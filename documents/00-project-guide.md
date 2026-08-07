# Project Guide Document

This file is the entry point for evaluating this project. It maps each
expected deliverable to where it actually lives in this repository. Every
"Where to look" cell points at a file or section that exists in the
codebase.

## Section A: Deliverables & Evaluation Map

| Requirement | Where to look |
|---|---|
| Working prototype | `documents/04-setup-instructions.md` (all three run options); source under `src/main/java/com/assignment/urlshortener/` |
| Architecture overview | `documents/01-architecture-and-design.md` (component diagram §3, data model §4, API design §5, request flow §7) |
| Greenfield scenario | `documents/03-scenarios-and-validation.md`, Scenario 1 |
| Brownfield scenario: expiration | `documents/03-scenarios-and-validation.md`, Scenario 2; `documents/01-architecture-and-design.md` §6 "Expiration" |
| Brownfield scenario: soft-delete endpoint | `documents/03-scenarios-and-validation.md`, Scenario 3; `documents/02-ai-engineering-log.md` §5 "Decision Log" (row "Soft-delete endpoint") |
| Ambiguous requirement scenario | `documents/03-scenarios-and-validation.md`, Scenario 4; `documents/02-ai-engineering-log.md` §5 "Decision Log" (row "Custom alias") |
| Setup instructions | `documents/04-setup-instructions.md` (full file) |
| Testing, future scope, and trade-offs | `README.md` "Future Scope" and "Design Trade-Offs"; `documents/02-ai-engineering-log.md` §10 "Risks and Secure AI Usage" |
| Requirement understanding & task decomposition | `documents/01-architecture-and-design.md` §1–2 (overview, scope); `documents/02-ai-engineering-log.md` (full file); `documents/03-scenarios-and-validation.md` (Decomposition subsection per scenario) |
| AI-assisted execution with traceability | `documents/02-ai-engineering-log.md` (full file: representative prompt plus decision log) |
| Quality gate: automated tests | `README.md` "Testing" (76 tests); run `./mvnw clean test` |
| Quality gate: security tests | `documents/02-ai-engineering-log.md` §5 "Decision Log" (row "Security validation"); `src/test/java/.../controller/SecurityValidationTest.java` |
| Quality gate: k6 performance | `documents/02-ai-engineering-log.md` §5 "Decision Log" (row "Performance testing"); `performance/k6/url-shortener-load.js` and `performance/k6/README.md` |
| Quality gate: Checkstyle/SpotBugs static analysis | `documents/02-ai-engineering-log.md` §5 "Decision Log" (row "Static analysis"); `README.md` "Static Analysis" (358 Checkstyle violations, 6 SpotBugs findings, both report-only); `pom.xml`; run `./mvnw checkstyle:check spotbugs:check` |
| Secure AI usage | `documents/02-ai-engineering-log.md` §10 "Risks and Secure AI Usage"; §2 "Working Method" |
| Human sign-off on high-impact changes | `documents/02-ai-engineering-log.md` §7 "Corrections Made During Review", §8 "AI Contribution and Ownership", §9 "Final Ownership Statement" |
| Project overview and deliverables summary | `README.md` (full file, including "Final Deliverables" and "Future Scope") |

## Section B: AI Usage by Scope Category

Sourced from `documents/02-ai-engineering-log.md`.

| Scope Category | How AI Was Used | My Role |
|---|---|---|
| Greenfield | Generated the initial entity, repository, service, controllers, and Base62 generator for the core create/redirect/analytics flow | Set acceptance criteria upfront, approved the layered design, reviewed API boundaries and collision handling, required tests to pass before acceptance |
| Brownfield | Implemented expiration and the soft-delete endpoint: new controller/service method, exception class, cache eviction | Scoped each change to one feature at a time, confirmed no regressions to existing endpoints, required the full suite to pass before accepting each change |
| Test and documentation improvements | Added security tests (SQLi-style, XSS-style input), k6 performance scripts, Checkstyle/SpotBugs wiring, and drafted initial documentation content | Kept static-analysis gates report-only, caught and corrected a stale test-count figure during review, owned final accuracy of all docs |
| Well-defined and ambiguous requirements | Implemented well-defined requirements directly (redirect, analytics); proposed an interpretation for the ambiguous "memorable URL" requirement | Decided which requirements needed clarification first; approved the custom-alias interpretation as clear, testable, and API-compatible |
