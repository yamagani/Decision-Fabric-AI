# NFR Requirements — Unit 2: Rule Management

## Unit Context
**Unit**: Unit 2 — Rule Management  
**Stories**: US-1.1, US-1.2, US-1.3  
**NFR Sources**: Global inception requirements (NFR-01 → NFR-07) + Unit 2-specific decisions

---

## 1. Performance Requirements

### PERF-01: Rule Management CRUD Response Time
**Requirement**: All synchronous rule management API operations (create, update, activate, deactivate, delete, list) SHALL respond within **500ms at p99** under normal load.  
**Rationale**: Rule management is an administrative path, not the hot evaluation path. 500ms is the accepted budget for operations that include database writes and DMN validation.  
**Measurement**: Micrometer `http.server.requests` timer tagged by `uri` and `method`.

### PERF-02: DMN Import Response Time
**Requirement**: `POST /api/v1/rules/import` (multipart `.dmn` upload + schema validation) SHALL respond within **2 seconds at p99** for files up to 1 MB.  
**Rationale**: DMN XML schema validation via Drools can be CPU-intensive for complex models. 2 seconds is acceptable for an infrequent import operation.

### PERF-03: DMN Schema Validation Timeout
**Requirement**: `DmnSchemaValidatorPort.validate()` SHALL complete within **1 second**. If it exceeds 1 second, throw `DmnValidationException` with timeout reason.  
**Implementation**: Wrap Drools validation call in a timeout using Kotlin `runWithTimeout` or `withTimeout` coroutine (or `Future.get(1, TimeUnit.SECONDS)`).

### PERF-04: Cache Read Latency
**Requirement**: `InMemoryRuleCache.getActiveVersions(ruleId)` SHALL complete in **sub-millisecond** time (O(1) `ConcurrentHashMap` lookup).  
**Rationale**: The cache is the hot path for Unit 3 decision evaluation; any blocking in cache reads directly degrades evaluation latency.

### PERF-05: Cache Reload Latency (Activation Event)
**Requirement**: Cache update triggered by `RuleLifecycleEvent.RuleVersionActivated` SHALL complete within **100ms** (single entry reload from DB).  
**Rationale**: Activation is infrequent; even with stale reads allowed (Q1=A), cache should be fresh within a human-imperceptible window.

---

## 2. Scalability Requirements

### SCALE-01: Stateless Application Tier
**Requirement**: `RuleManagementService` and all REST adapters SHALL contain no instance-level mutable state. All state is in PostgreSQL or `InMemoryRuleCache`.  
**Rationale**: Enables horizontal scaling via ECS/EKS auto-scaling groups without session affinity requirements.

### SCALE-02: Cache Size Bound (Configurable Max Bytes)
**Requirement**: `InMemoryRuleCache` SHALL enforce a configurable maximum heap footprint. Default: **200 MB**. Configurable via `rule.cache.max-bytes-mb` property.  
**Eviction Policy**: When adding a new entry would exceed the cap, evict the entry with the oldest `activatedAt` timestamp first. Log a `WARN` message: `"InMemoryRuleCache evicted rule {ruleId} v{version} due to capacity constraint (limit: {limit}MB)"`.  
**Rationale**: A single `.dmn` file may be up to 1 MB; with hundreds of active versions this could consume significant heap. A byte-bound with oldest-first eviction balances memory safety with cache freshness.

### SCALE-03: Paginated List Queries
**Requirement**: All list endpoints (`GET /rules`, `GET /rule-sets`) SHALL be paginated with a maximum page size of **100 items**. Default page size: **20 items**.  
**Rationale**: Unbounded queries against PostgreSQL tables will degrade under growth; pagination keeps query time predictable.

### SCALE-04: Cache Concurrency — Eventual Consistency Model
**Requirement**: The `InMemoryRuleCache` SHALL use a `ConcurrentHashMap` **without additional read locks**. Readers that execute during a cache entry update MAY observe the pre-update state (stale read) for the duration of the update operation (expected < 10ms).  
**Rationale**: Q1=A. Rule management is a low-frequency administrative operation. The brief window of stale reads is acceptable and avoids blocking the high-frequency evaluation read path. This is a deliberate consistency trade-off.

---

## 3. Availability & Reliability Requirements

### AVAIL-01: 99.9% Uptime Target
**Requirement**: Unit 2 endpoints SHALL contribute to the overall system 99.9% availability SLA. This means < 8.7 hours downtime per year.  
**Implementation**: Achieved via multi-AZ PostgreSQL deployment (RDS Multi-AZ), ECS/EKS health checks, and Spring Boot Actuator `/actuator/health` endpoint.

### AVAIL-02: Graceful Degradation on Cache Warm-Up Failure
**Requirement**: If a DMN XML entry fails to parse during startup cache warm-up (e.g., corrupt stored XML), the warm-up SHALL log a `WARN` and skip that entry — it SHALL NOT prevent the application from starting.  
**Implementation**: `@EventListener(ApplicationReadyEvent::class)` wraps each entry load in `try/catch`; failed entries are skipped and logged.

### AVAIL-03: Database Transaction Isolation
**Requirement**: All write operations to `rules` and `rule_versions` tables SHALL use **SERIALIZABLE** isolation for the pessimistic-lock path (Q2=C). Read operations use **READ_COMMITTED** (Spring default).  
**Implementation**: `@Transactional(isolation = Isolation.SERIALIZABLE)` on `RuleManagementService` write methods; explicit `SELECT FOR UPDATE` in `PostgreSqlRuleRepositoryAdapter` via Spring Data JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)`.

### AVAIL-04: No Data Loss on Soft Delete
**Requirement**: Soft-deleted rules and all version DMN XML SHALL remain in PostgreSQL indefinitely unless explicitly purged via the manual purge API (Q3=C).  
**Rationale**: Provides audit trail and rollback capability.

---

## 4. Security Requirements

### SEC-01: Input Validation — DMN XML Injection Prevention
**Requirement**: All DMN XML inputs (create, update, import, validate) SHALL be:
1. Parsed with an **XXE-safe XML parser** (`XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES = false`, `XMLInputFactory.SUPPORT_DTD = false`)
2. Size-limited to **1 MB** before parsing begins
3. Validated against the OMG DMN 1.4 schema via `DmnSchemaValidatorPort`  
**Rationale**: XML External Entity (XXE) injection is an OWASP Top 10 vulnerability. DMN XML is user-supplied; an unsafe parser could expose internal filesystem or SSRF.

### SEC-02: Role Enforcement on All Endpoints
**Requirement**: Every `RuleApiController` and `RuleSetApiController` endpoint SHALL be covered by Spring Security `@PreAuthorize` or `SecurityFilterChain` path matchers. No endpoint SHALL be `permitAll()`.  
**Write operations**: require `ROLE_RULE_ADMIN`  
**Read operations**: require at minimum `ROLE_RULE_READER`

### SEC-03: No DMN XML in Structured Logs
**Requirement**: DMN XML content SHALL NOT be written to application logs at any level (DEBUG/INFO/WARN/ERROR). Log headers, IDs, and validation error summaries only.  
**Rationale**: DMN rules may contain sensitive business logic; logging XML would expose it in CloudWatch.

### SEC-04: Correlation ID and User ID in All Audit Entries
**Requirement**: Every `rule_audit_log` entry SHALL contain non-null `user_id` (from JWT `sub` claim) and `correlation_id` (from MDC).  
**Validation**: Commands that arrive without `userId` or `correlationId` SHALL be rejected with `400 Bad Request`.

### SEC-05: Manual Purge API — SYSTEM_ADMIN Only
**Requirement**: The `DELETE /rules/{id}/versions/{v}` purge endpoint (Q3=C) SHALL require `ROLE_SYSTEM_ADMIN`. It is intentionally scoped more narrowly than `RULE_ADMIN` to prevent accidental data loss.  
**Guard**: Only `INACTIVE` versions may be purged. Attempts to purge `DRAFT` or `ACTIVE` versions return `422 Unprocessable Entity`.

---

## 5. Maintainability Requirements

### MAINT-01: Unit Test Coverage ≥ 80%
**Requirement**: Domain classes (`Rule`, `RuleSet`, `RuleVersion`, all value objects) SHALL achieve ≥ **80% line coverage** measured by JaCoCo.  
**Scope**: Domain + application service layers; infrastructure adapters tested via integration tests.

### MAINT-02: Integration Tests with TestContainers
**Requirement**: `PostgreSqlRuleRepositoryAdapter` and `DroolsDmnSchemaValidatorAdapter` SHALL each have a dedicated integration test class using `@Testcontainers` with a real PostgreSQL container.  
**Coverage**: All CRUD operations, pessimistic lock behaviour, migration correctness (V2 schema applied).

### MAINT-03: Controller Slice Tests
**Requirement**: `RuleApiController` and `RuleSetApiController` SHALL have `@WebMvcTest` slice tests covering:
- Correct HTTP status codes for all domain exceptions
- Role enforcement (403 for insufficient role, 401 for unauthenticated)
- Request validation (400/422 for invalid inputs)

### MAINT-04: Flyway Migration Reversibility Documentation
**Requirement**: `V2__rules_schema.sql` SHALL include a comment block at the top documenting what each statement creates and the rollback strategy (manual steps), since Flyway community edition does not support automatic rollback.

---

## 6. Observability Requirements

### OBS-01: Structured Log Fields for Rule Operations
**Requirement**: Every log entry in `RuleManagementService` and adapters SHALL include MDC fields: `correlationId`, `userId`, `ruleId` (where applicable), `ruleSetId` (where applicable).  
**Implementation**: MDC populated by `CorrelationIdFilter` (correlationId) and extracted from JWT in service layer.

### OBS-02: Micrometer Metrics
**Requirement**: The following custom metrics SHALL be registered via `MeterRegistry`:

| Metric Name | Type | Tags | Description |
|---|---|---|---|
| `rule.cache.size` | Gauge | — | Current number of entries in `InMemoryRuleCache` |
| `rule.cache.bytes` | Gauge | — | Estimated current heap usage of cache in bytes |
| `rule.cache.evictions` | Counter | `reason` | Count of cache evictions (capacity / event) |
| `rule.lifecycle.events` | Counter | `action` | Count of each `RuleLifecycleEvent` type triggered |
| `rule.dmn.validation.duration` | Timer | `result` (valid/invalid) | Time spent in `DmnSchemaValidatorPort.validate()` |

### OBS-03: Health Indicator for Cache
**Requirement**: A Spring Boot `HealthIndicator` bean (`RuleCacheHealthIndicator`) SHALL report:
- `UP` when cache is populated and below 90% of its byte cap
- `DEGRADED` when cache is between 90% and 100% of byte cap
- `DOWN` if cache failed to warm up (exception during startup)

---

## 7. Non-Functional Decisions Summary

| Concern | Decision | Rationale |
|---|---|---|
| Cache consistency | Eventual (stale reads OK, no read lock) | Preserve evaluation throughput; reload < 10ms |
| Concurrent write safety | Pessimistic lock (`SELECT FOR UPDATE`) | Prevents lost-update on version counter; management path is low-frequency |
| Version retention | Manual purge API only (no auto-prune) | Operator control; audit trail preserved by default |
| Cache memory safety | Configurable max-bytes (200 MB default), oldest-first eviction | Heap safety without operational complexity |
| Rule name search | `(status, rule_set_id)` index + `pg_trgm` trigram on `name` | Both operational list and search use cases served |
| XML security | XXE-safe parser + 1 MB size limit before parsing | OWASP XXE mitigation |
| Purge authorization | `SYSTEM_ADMIN` only (stricter than `RULE_ADMIN`) | Prevent accidental irreversible deletion |
