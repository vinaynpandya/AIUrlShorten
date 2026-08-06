# Final Engineering Summary

## 1. Objective and result

I built a Java 17 and Spring Boot URL-shortener prototype that converts a
high-level requirement into a runnable and reviewable engineering outcome.
The project demonstrates both the final code and the way I used Claude Code
as an engineering accelerator under human control.

The final implementation supports URL creation, seven-character Base62
codes, HTTP redirects, analytics, optional expiration, optional custom
aliases, a static browser interface, H2 local execution, PostgreSQL shared
persistence, optional Redis cache-aside, two application instances, NGINX,
Docker Compose, and application-instance response headers.

## 2. How I approached the design

I did not begin with the complete distributed architecture. I first clarified
the functional and non-functional requirements, defined the entity and API
contracts, and created a simple Spring Boot and H2 greenfield architecture.

After the core flow worked, I reviewed the design gaps and introduced focused
enhancements:

1. I added a database uniqueness constraint and bounded Base62 generation
   attempts.
2. I used an atomic repository update for analytics.
3. I added expiration as a backward-compatible brownfield change.
4. I resolved the ambiguous memorable-URL requirement as a validated custom
   alias.
5. I added a static UI and corrected the root-route conflict.
6. I replaced per-instance storage with a PostgreSQL scalable profile.
7. I added optional Redis cache-aside for repeated redirect mappings.
8. I added two stateless application instances and NGINX.
9. I used Docker Compose to make the environment repeatable.
10. I validated creation on `app1` and redirect on `app2`.

This sequence made every major technology a response to an identified
engineering need.

## 3. How I used Claude Code

I used Claude Code to accelerate requirement analysis, file planning,
implementation, test generation, debugging, configuration, Docker support,
and documentation.

I did not accept the output without review. My interventions included:

- requiring a design and file plan before implementation;
- limiting Claude to approved files;
- selecting the API contracts and HTTP statuses;
- requiring atomic analytics updates;
- correcting Spring Boot test imports and dependencies;
- ensuring expired links did not increment analytics;
- defining custom-alias product rules;
- correcting the static-resource route conflict;
- keeping Redis optional;
- keeping PostgreSQL as the source of truth;
- requiring database validation on a cache hit;
- diagnosing Docker, environment, and port issues manually;
- protecting `.env` from Git;
- restricting final claims to recorded evidence.

## 4. Model and token decisions

I used Sonnet 5 because the project primarily required bounded coding and
review tasks. Sonnet 5 provided sufficient Java and Spring Boot capability
without using a higher-cost model for every interaction.

I controlled token use by separating the work into phases, using file
allowlists, requiring concise plans and summaries, storing stable context in
`CLAUDE.md`, running Git and environment commands manually, and stopping the
documentation session when it became unnecessarily long.

The model supported the work, but decomposition and engineering review
remained the main quality controls.

## 5. Validation

The final source contains 37 test methods across six test classes. The tests
cover the generator, service, controllers, integration flow, expiration,
custom aliases, caching behavior, analytics, and the instance header.

The recorded scalable validation showed:

```text
Creation:
HTTP/1.1 201
X-App-Instance: app1

Redirect:
HTTP/1.1 302
X-App-Instance: app2
Location: https://example.com/final-scalable-test
```

This demonstrates request distribution and shared persistence across the two
application instances. It does not establish production latency, throughput,
automatic failover, or an availability percentage.

## 6. Principal decisions and trade-offs

| Decision | Benefit | Trade-off |
|---|---|---|
| Random seven-character Base62 codes | The codes are compact and non-sequential. | Collision handling remains probabilistic and database-dependent. |
| H2 default profile | Local setup is fast. | H2 does not reproduce every PostgreSQL behavior. |
| PostgreSQL scalable profile | Both instances share durable relational state. | The demonstrated database is still a single service. |
| Atomic synchronous analytics | Click counts are immediately consistent. | Every redirect performs a database write. |
| Optional Redis cache-aside | Repeated redirect mappings can avoid a database read. | The system has additional infrastructure and still writes analytics. |
| `302 Found` | The service retains control for analytics and expiration. | Clients cannot permanently cache the redirect as they could with `301`. |
| Alias no-reuse | A previously shared alias cannot be taken over. | The namespace is consumed permanently. |
| NGINX plus two app instances | The prototype demonstrates horizontal request routing. | One local NGINX does not create complete high availability. |

## 7. Known limitations

The current generated-code path does not retry a save-time database
uniqueness race. The cache-miss path does not inspect the row count returned
by the conditional analytics update. These are documented production
hardening items.

The prototype also lacks authentication, authorization, ownership, rate
limiting, malicious-link screening, TLS, production secrets, replicated
infrastructure, asynchronous analytics, load testing, failure testing, and
multi-region operations.

## 8. Deliverable cross-reference

| Assessment area | Evidence |
|---|---|
| Requirement understanding | `01-engineering-design-and-architecture.md` |
| Task decomposition | `02-ai-prompt-and-decision-log.md` and the scenario tasks |
| Greenfield scenario | Core creation, redirect, and analytics section |
| Brownfield scenario | Expiration section |
| Ambiguous requirement | Custom-alias section |
| Codebase reasoning | Impacted classes and data-flow analysis |
| AI-assisted execution | Prompt and decision log |
| Generated, edited, and reviewed output | Engineer review table and intervention descriptions |
| Validation and risk control | `03-scenarios-validation-and-risks.md` |
| Setup instructions | `04-setup-instructions.md` |
| Architecture overview | Greenfield and final diagrams |
| Model and token reasoning | Prompt log and this summary |
| Engineer ownership | Review decisions, manual validation, Git control, and limitations |

## 9. Conclusion

The project demonstrates that I used AI as an accelerator rather than as a
replacement for engineering judgment. I established the context, decomposed
the work, reviewed generated output, corrected defects, introduced
scalability only after identifying the relevant gaps, validated the system,
and retained ownership of the final decisions and limitations.
