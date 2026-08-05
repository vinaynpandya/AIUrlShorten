# Risks and Trade-offs — URL Shortener

## Random-code collision

Base62 7-character codes have `62^7` (~3.52 trillion) possible values.
Collisions are handled via a bounded retry loop plus a database unique
constraint as the final guarantee (`architecture.md` §8). This applies
identically in both the default and `scalable` profiles, since code
generation happens in the application layer, not the database.

## Concurrent insert race

A collision between two concurrent creates is resolved by the database
unique constraint rejecting the second insert, not by a
pre-check-then-insert pattern, avoiding a TOCTOU race
(`architecture.md` §8). In the `scalable` profile this remains safe with
two application instances writing to the same shared PostgreSQL
database, since uniqueness is enforced at the database level regardless
of which instance issues the insert.

## Synchronous analytics latency

Click-count updates happen synchronously and atomically within the
redirect request (`architecture.md` §9), adding a database write to
every redirect. This is unchanged in the `scalable` profile: even on a
Redis cache hit, a conditional atomic `UPDATE` still runs against
PostgreSQL for the click count (`architecture.md` §14) — the cache
avoids re-reading the redirect target, not the click-count write.

## H2 durability limitation

The default profile's embedded H2 database is not durable across
process restarts. **Mitigated** in the `scalable` profile, which uses
PostgreSQL as the source of truth with a persistent Docker volume
(`pg_data`).

## No authentication

Neither profile implements authentication or authorization on any
endpoint. Documented as a known limitation, not addressed in this
phase.

## No rate limiting

Neither profile implements rate limiting or per-IP/per-key quotas on
creation or redirect endpoints. Documented as a known limitation.

## No malicious URL detection

Neither profile validates the destination of a shortened URL against
malware/phishing block lists; validation is limited to well-formed
HTTP/HTTPS syntax (`architecture.md` §10).

## Single-instance availability

**Mitigated** in the `scalable` profile: `app1` and `app2` run behind
an NGINX load balancer (`least_conn`, passive health checks), sharing
the same PostgreSQL and Redis backends, so either instance can serve
any request. This was verified directly — a URL created via NGINX was
served by `app1`, and its redirect via NGINX was served by `app2`
(confirmed via the `X-App-Instance` response header), demonstrating
that requests are distributed and persistence is shared. The default
(local) profile remains single-instance. No failover or availability
testing (e.g., killing an instance under load) has been performed.

## No distributed cache

**Implemented** in the `scalable` profile: a single Redis instance,
shared by `app1` and `app2`, caches resolved redirect targets in a
cache-aside pattern (`architecture.md` §14). Redis is optional — a
cache miss or Redis outage falls back to a direct PostgreSQL read, and
PostgreSQL remains the sole source of truth. The default (local)
profile has caching disabled (`app.cache.enabled=false`). Trade-off:
because the cache is populated lazily and evicted only when a resolve
fails its conditional update, a URL edited or deactivated through a
path other than the redirect flow could theoretically be served stale
until its TTL expires — no such path exists in the current API, so this
is not currently reachable.

## No asynchronous analytics

Click counting remains synchronous and atomic in both profiles (see
"Synchronous analytics latency" above); no message broker or async
pipeline has been introduced.
