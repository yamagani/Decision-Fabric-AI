# NFR Requirements Plan — Unit 2: Rule Management

## Unit Context
**Unit**: Unit 2 — Rule Management
**NFR Coverage**: NFR-01 (Performance), NFR-02 (Scalability), NFR-03 (Availability), NFR-04 (Security), NFR-06 (Maintainability)
**Tech Stack**: Kotlin / Spring Boot 3.2.3, PostgreSQL + Flyway, Drools DMN, `InMemoryRuleCache` (ConcurrentHashMap), Micrometer

---

## Execution Checklist

- [x] Step 1: Analyse functional design artifacts
- [x] Step 2: Create NFR requirements plan (this document)
- [x] Step 3: Answer clarification questions (Section A)
- [x] Step 4: Generate nfr-requirements.md
- [x] Step 5: Generate tech-stack-decisions.md
- [x] Step 6: Validate NFR completeness

---

## Context Notes

The following NFRs are already established globally in the inception requirements and apply directly to Unit 2:
- **NFR-01**: `p99 < 200ms` for rule-only evaluation (cache-served); rule management CRUD < 500ms target
- **NFR-02**: Stateless application tier; PostgreSQL scales read replicas; cache reduces DB load
- **NFR-03**: 99.9% uptime; multi-AZ PostgreSQL; stateless Spring Boot
- **NFR-04**: TLS 1.2+, KMS at rest, structured logging, no PII in logs
- **NFR-06**: Clean architecture, 80% unit test coverage minimum

The questions below focus on **Unit 2-specific NFR decisions** that are not yet resolved by the global context.

---

## Section A: NFR Clarification Questions

---

### Question 1
**Cache concurrency model** — During a rule version activation (which triggers `InMemoryRuleCache` reload), concurrent read requests to the cache:

A) **Accept stale reads** — readers see the old cache state during the short reload window; consistency is eventual (reload completes within milliseconds); no locking during reads
B) **Block during reload** — use a read-write lock (`ReentrantReadWriteLock`); write lock held only for the duration of the entry swap; readers briefly block but always get consistent state
C) **Copy-on-write** — maintain two cache snapshots; swap the reference atomically; readers always see a fully consistent snapshot; no blocking at all; slightly higher memory usage

[Answer]: A

---

### Question 2
**Optimistic locking for concurrent rule updates** — When two `RULE_ADMIN` users submit `PUT /rules/{id}` simultaneously (both attempting to add a new DRAFT version):

A) **Optimistic locking** — use `@Version` column on the `rules` JPA entity; second writer gets `409 Conflict` with message "Rule was modified concurrently; please retry"
B) **Last write wins** — no optimistic lock; both writes succeed independently, each creating their own DRAFT version (since branch model always appends, no data is lost)
C) **Pessimistic locking** — `SELECT FOR UPDATE` on the rule row during the update transaction; second writer blocks until the first finishes

[Answer]: C

---

### Question 3
**INACTIVE version retention / pruning** — Over time, rules accumulate many `INACTIVE` DRAFT and deactivated versions. Should the system prune them?

A) **Retain indefinitely** — no automatic pruning; all historical versions kept forever; database storage is the only limit
B) **Configurable retention** — retain the last N versions (configurable, default = 50) via a scheduled background job; older INACTIVE versions are hard-deleted; ACTIVE versions are never purged
C) **Manual purge API** — no automatic pruning; provide an admin-only `DELETE /rules/{id}/versions/{v}` endpoint to manually discard individual INACTIVE versions

[Answer]: C

---

### Question 4
**Cache memory bound** — The `InMemoryRuleCache` holds all ACTIVE DMN XML in heap. Given that a single `.dmn` file can be up to 1 MB:

A) **No explicit bound** — the cache holds all ACTIVE versions regardless of count; rely on JVM heap sizing (`-Xmx`) as the only limit; alert if heap > 80%
B) **Configurable max entries** — cap the number of cached `(ruleId, version)` entries (default = 1000); if exceeded, evict oldest-activated entries; log a warning
C) **Configurable max bytes** — cap total cache size in MB (default = 200 MB); if adding an entry would exceed the cap, evict smallest-recently-activated entries; log a warning

[Answer]: C

---

### Question 5
**Database query performance for list endpoints** — `GET /api/v1/rules` may be called frequently by operators and monitoring tools:

A) **Index on status + rule_set_id only** — standard index on `(status, rule_set_id)`; acceptable for most query patterns
B) **Full-text search on rule name** — add `pg_trgm` trigram index on `rules.name` to support `?search=` partial name matching in list endpoints
C) **Index on status only** — minimal indexing; acceptable given expected rule counts (hundreds, not millions)

[Answer]: A,B

---

*Once all `[Answer]:` tags are filled, notify me and I will generate nfr-requirements.md and tech-stack-decisions.md.*
