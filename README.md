# AI-Assisted URL Shortener

A production-oriented URL shortener prototype built with Java and Spring Boot.

The application supports:

- Generating seven-character Base62 short codes
- Redirecting short URLs using HTTP 302
- Basic click analytics
- Optional URL expiration
- Optional memorable custom aliases
- Request validation and structured error responses
- Health monitoring through Spring Boot Actuator
- A web UI for creating short URLs and viewing analytics

This project also demonstrates controlled AI-assisted software engineering.
AI was used for requirement analysis, implementation, testing, debugging,
documentation, and code review. All changes were reviewed and approved by
the engineer.

---

## Technology Stack

- Java 17
- Spring Boot
- Maven
- Spring Web MVC
- Spring Data JPA
- Jakarta Validation
- H2 Database (default/local profile)
- PostgreSQL and Redis (`scalable` profile)
- NGINX (`scalable` profile load balancer)
- Docker / Docker Compose (`scalable` profile deployment)
- Spring Boot Actuator
- JUnit 5
- Mockito
- MockMvc
- Claude Code for AI-assisted engineering

---

## Prerequisites

Install:

- Java 17 or later
- Git
- A terminal or IntelliJ IDEA
- Docker and Docker Compose (only required for the `scalable` deployment profile)

Verify Java:

```bash
java -version
```

---

## Running Locally (Default Profile)

The default profile requires no external services:

```bash
./mvnw spring-boot:run
```

- Uses an embedded H2 database.
- Redis caching is disabled (`app.cache.enabled=false`).
- Application is available at `http://localhost:8080`.

---

## Running the Scalable Deployment (Docker Compose)

The `scalable` Spring profile runs the application against PostgreSQL and
Redis, with two application instances behind an NGINX load balancer.

1. Copy `.env.example` to `.env` and adjust `DB_USERNAME`/`DB_PASSWORD` as needed.
2. Start the stack:

```bash
docker compose up --build
```

This starts:

| Service | Role | Address |
|---|---|---|
| `nginx` | Public entry point, load-balances across `app1`/`app2` | `http://localhost:8080` |
| `app1` | Spring Boot instance (`scalable` profile) | `http://localhost:8081` (direct, bypasses NGINX) |
| `app2` | Spring Boot instance (`scalable` profile) | `http://localhost:8082` (direct, bypasses NGINX) |
| `postgres` | Source of truth database, shared by both instances | internal only |
| `redis` | Optional redirect cache, shared by both instances | internal only |

Every response includes an `X-App-Instance` header identifying which
instance (`app1` or `app2`) served the request, so load balancing across
NGINX can be observed directly.

Redis is optional: if it is unavailable, the application falls back to
reading directly from PostgreSQL, which remains the source of truth for
all data.

### Verified Behavior

- URL creation through NGINX (`POST http://localhost:8080/api/v1/urls`) returned `HTTP 201`, served by `app1`.
- Redirect through NGINX (`GET http://localhost:8080/{shortCode}`) returned `HTTP 302`, served by `app2`.
- Together these confirm NGINX load balancing across both instances and that both instances share the same PostgreSQL persistence layer.

This is a functional-correctness check, not a performance or availability
benchmark — no load, latency, or uptime figures have been measured.

---

## Architecture

See [`docs/architecture.md`](docs/architecture.md) for the full component,
control-flow, and deployment architecture, including a diagram of the
`scalable` Docker Compose topology.
