# k6 Performance Tests

Load tests for the URL shortener's redirect path (Redis-cached), URL creation,
and analytics lookup. These are performance tests only — they do not replace
the functional/security test suites under `src/test/java`.

## What the script does

`url-shortener-load.js` runs three scenarios concurrently:

1. **`redirects`** — the primary, redirect-heavy workload. All virtual users
   hit `GET /{shortCode}` for a single short code created once in `setup()`
   and pre-warmed into Redis with one redirect request, so this is
   deliberately a hot-key, cache-hit workload. The script does **not** follow
   the redirect to the external destination (`redirects: 0`); it only
   validates the redirect response itself:
   - status is `302`
   - a `Location` header is present
   - an `X-App-Instance` header is present (proves the NGINX-fronted,
     multi-instance setup in `docker-compose.yml` is actually load-balancing)

   Every redirect request sends a realistic Chrome `User-Agent` header. No
   `CF-IPCountry` header is sent and no country-breakdown analytics are
   exercised — the `country` field and the "by country" click-analytics
   breakdown were removed from the current implementation, so testing them
   would test a feature that no longer exists.

2. **`creations`** — a much lower-rate background workload against
   `POST /api/v1/urls`. Every request uses a unique `originalUrl`
   (`https://example.com/<prefix>/<vu>-<iter>-<timestamp>-<random>`) so
   short-code generation and the DB unique constraint are exercised
   realistically instead of hammering one row.

3. **`analyticsLookup`** — a small background workload against
   `GET /api/v1/urls/{shortCode}/analytics` for the same short code created
   in `setup()`.

Creation and analytics VUs default to 2% and 1% of the peak redirect VU
count (minimum 1 VU each), so they stay background traffic rather than
competing with the redirect measurement — this is what keeps creation "at a
much lower rate than redirects" as required.

## Metrics tracked

| Requirement | Where it comes from |
|---|---|
| Total requests | built-in `http_reqs` (see end-of-run summary) |
| Requests per second | built-in, derived from `http_reqs` over test duration |
| Request failure rate | built-in `http_req_failed` |
| p50 / p95 / p99 latency (overall) | built-in `http_req_duration`, with `summaryTrendStats` configured to print `p(50)`, `p(90)`, `p(95)`, `p(99)` |
| Redirect checks passed/failed | custom counters `redirect_checks_passed`, `redirect_checks_failed` |
| Creation latency | custom trend `creation_duration` |
| Redirect latency | custom trend `redirect_duration` |
| Analytics latency | custom trend `analytics_duration` |

## Thresholds (provisional)

```js
http_req_failed:   ["rate<0.01"]   // < 1% failures across all requests
redirect_duration: ["p(95)<500"]   // redirect p95 < 500ms
creation_duration: ["p(95)<1000"]  // creation p95 < 1s
```

These are **starting points**, not validated SLAs — see Limitations below.
k6 reports threshold pass/fail in the summary; it does not abort the run on
its own.

## Environment variables

| Variable | Default | Meaning |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | Target base URL (NGINX in the Docker Compose setup, or a single instance) |
| `REDIRECT_VUS` | `10` | Virtual users for the redirect scenario (non-staged mode) |
| `REDIRECT_DURATION` / `DURATION` | `30s` | Duration of the redirect scenario (non-staged mode) |
| `REDIRECT_SLEEP_SECONDS` | `0.2` | Think time between redirect requests per VU |
| `CREATE_VUS` | 2% of peak redirect VUs (min 1) | Virtual users for the creation scenario |
| `CREATE_DURATION` / `DURATION` | `30s` | Duration of the creation scenario (non-staged mode) |
| `CREATE_SLEEP_SECONDS` | `1` | Think time between creation requests per VU |
| `ANALYTICS_VUS` | 1% of peak redirect VUs (min 1) | Virtual users for the analytics scenario |
| `ANALYTICS_DURATION` / `DURATION` | `30s` | Duration of the analytics scenario (non-staged mode) |
| `ANALYTICS_SLEEP_SECONDS` | `2` | Think time between analytics requests per VU |
| `STAGED` | `false` | When `true`, the redirect scenario ramps through 50 → 100 → 250 → 500 → 1000 VUs instead of using `REDIRECT_VUS` |
| `STAGE_DURATION` | `1m` | Duration of each stage when `STAGED=true` (5 stages ⇒ total staged run ≈ 5 × this value) |

## Installing k6

Not installed in this environment. On macOS:

```bash
brew install k6
```

Other platforms: https://grafana.com/docs/k6/latest/set-up/install-k6/
(Docker alternative: `docker run --rm -i grafana/k6 run - <performance/k6/url-shortener-load.js`,
adjusting `BASE_URL` to reach the host, e.g. `http://host.docker.internal:8080`.)

## Commands

Run the app first (`./mvnw spring-boot:run`, or `docker compose up` for the
full NGINX + 2-instance + Redis + Postgres `scalable` setup) before running
any of these.

**Smoke test** (2 VUs, 10s — confirm the script and target work before
spending time on real load):

```bash
REDIRECT_VUS=2 DURATION=10s k6 run performance/k6/url-shortener-load.js
```

**100-user test:**

```bash
REDIRECT_VUS=100 DURATION=1m k6 run performance/k6/url-shortener-load.js
```

**250-user test:**

```bash
REDIRECT_VUS=250 DURATION=1m k6 run performance/k6/url-shortener-load.js
```

**Complete staged test up to 1,000 users** (ramps 50 → 100 → 250 → 500 →
1000, 1 minute per stage by default, ~5 minutes total):

```bash
STAGED=true k6 run performance/k6/url-shortener-load.js
```




