# Logical Components — Unit 2: Rule Management

## Overview
This document describes all logical components introduced in Unit 2, their responsibilities, interfaces, and collaborations. Infrastructure/cloud components are referenced but not designed here (covered in aidlc-docs/inception/application-design/).

---

## Component Map

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        INBOUND ADAPTERS                                      │
│  ┌──────────────────────┐   ┌──────────────────────────┐                   │
│  │  RuleApiController   │   │  RuleSetApiController    │                   │
│  │  (REST, /api/v1/     │   │  (REST, /api/v1/         │                   │
│  │   rules/*)           │   │   rule-sets/*)           │                   │
│  └──────────┬───────────┘   └────────────┬─────────────┘                   │
└─────────────┼──────────────────────────  ┼ ──────────────────────────────--┘
              │                            │
              ▼                            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      APPLICATION SERVICE                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    RuleManagementService                             │   │
│  │  implements: RuleManagementUseCase                                   │   │
│  │  uses ports: RuleRepositoryPort, DmnSchemaValidatorPort              │   │
│  │  publishes:  RuleLifecycleEvent (via ApplicationEventPublisher)      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└───────┬─────────────────────────┬────────────────────────┬──────────────────┘
        │                         │                        │
        ▼                         ▼                        ▼
┌───────────────┐  ┌─────────────────────────┐  ┌─────────────────────────┐
│  REPOSITORY   │  │  DMN VALIDATOR ADAPTER  │  │  AUDIT LOG ADAPTER      │
│  ADAPTER      │  │  DroolsDmnSchema        │  │  RuleAuditLogAdapter    │
│  PostgreSql   │  │  ValidatorAdapter       │  │  (JdbcTemplate)         │
│  RuleRepo     │  └─────────────────────────┘  └─────────────────────────┘
│  Adapter      │
└───────┬───────┘
        │                         ▲ RuleLifecycleEvent (AFTER_COMMIT)
        ▼                         │
┌──────────────┐   ┌──────────────────────────────────────────────────────┐
│  PostgreSQL  │   │                  InMemoryRuleCache                    │
│  (rules,     │   │  @Component, @TransactionalEventListener              │
│  rule_sets,  │   │  ConcurrentHashMap<RuleId, List<CachedRuleVersion>>   │
│  rule_vers.) │   │  warm-up on ApplicationReadyEvent                     │
└──────────────┘   └──────────────────────────────────────────────────────┘
                                  │ consumed by
                                  ▼
                         Unit 3: DecisionEvaluationService
```

---

## Component 1: `RuleManagementService`

**Type**: Application Service (Spring `@Service`)  
**Package**: `com.decisionfabric.application.rule`  
**Implements**: `RuleManagementUseCase`  
**Scope**: Singleton  

**Responsibilities**:
- Orchestrates all rule and rule-set lifecycle commands
- Validates commands, delegates to domain aggregates, persists via ports, publishes events
- Enforces `@Transactional` boundaries for write operations
- Delegates DMN validation to `DmnSchemaValidatorPort` before any persist

**Transaction boundaries**:

| Method | Isolation | Lock |
|---|---|---|
| `createRule` | `SERIALIZABLE` | None (new row) |
| `updateRule` | `SERIALIZABLE` | `PESSIMISTIC_WRITE` on `rules` row |
| `activateVersion` | `SERIALIZABLE` | `PESSIMISTIC_WRITE` on `rules` row |
| `deactivateVersion` | `SERIALIZABLE` | `PESSIMISTIC_WRITE` on `rules` row |
| `discardVersion` | `SERIALIZABLE` | `PESSIMISTIC_WRITE` on `rules` row |
| `deleteRule` | `SERIALIZABLE` | `PESSIMISTIC_WRITE` on `rules` row |
| `importDmn` | `SERIALIZABLE` | None (new row) |
| `createRuleSet` | `READ_COMMITTED` | None |
| `deleteRuleSet` | `SERIALIZABLE` | `PESSIMISTIC_WRITE` on `rule_sets` row + guard query |
| All read methods | `READ_COMMITTED` | None |

**Collaborators** (via ports):
- `RuleRepositoryPort` — load/save domain aggregates
- `DmnSchemaValidatorPort` — validate DMN XML
- `ApplicationEventPublisher` — publish `RuleLifecycleEvent`

---

## Component 2: `RuleApiController`

**Type**: Inbound REST Adapter (Spring `@RestController`)  
**Package**: `com.decisionfabric.adapter.inbound.rest.rule`  
**Base path**: `/api/v1/rules`  

**Responsibilities**:
- Parse and validate HTTP requests (Jakarta Bean Validation)
- Translate HTTP request DTOs → application commands
- Invoke `RuleManagementUseCase`
- Translate domain results → HTTP response DTOs
- Handle multipart file upload for DMN import

**Security**: Role enforcement via `SecurityFilterChain` path matchers (no per-method annotations needed for standard CRUD; `@PreAuthorize("hasRole('SYSTEM_ADMIN')")` on purge endpoint only).

**Request / Response DTOs** (`com.decisionfabric.adapter.inbound.rest.rule.dto`):

| DTO | Direction | Fields |
|---|---|---|
| `CreateRuleRequest` | In | `ruleSetId`, `name`, `description`, `dmnXml` |
| `UpdateRuleRequest` | In | `name?`, `description?`, `dmnXml` |
| `RuleResponse` | Out | `id`, `ruleSetId`, `ruleSetName`, `name`, `description`, `status`, `versions[]`, `latestVersion`, `activeVersionCount`, `createdBy`, `createdAt`, `updatedAt` |
| `RuleListResponse` | Out | `content: List<RuleResponse>`, `page`, `size`, `totalElements`, `totalPages` |
| `RuleVersionResponse` | Out | `version`, `status`, `createdBy`, `createdAt`, `activatedAt?`, `activatedBy?` |
| `DmnImportResponse` | Out | `ruleId`, `ruleSetId`, `name`, `version`, `status` |
| `DmnValidationResponse` | Out | `valid`, `errors: List<String>` |

---

## Component 3: `RuleSetApiController`

**Type**: Inbound REST Adapter (Spring `@RestController`)  
**Package**: `com.decisionfabric.adapter.inbound.rest.rule`  
**Base path**: `/api/v1/rule-sets`  

**Responsibilities**:
- CRUD for `RuleSet` lifecycle
- Delegates to `RuleManagementUseCase`

**Request / Response DTOs**:

| DTO | Direction | Fields |
|---|---|---|
| `CreateRuleSetRequest` | In | `name`, `description` |
| `RuleSetResponse` | Out | `id`, `name`, `description`, `status`, `ruleCount`, `activeRuleCount`, `createdBy`, `createdAt`, `updatedAt` |
| `RuleSetListResponse` | Out | `content: List<RuleSetResponse>`, pagination fields |

---

## Component 4: `PostgreSqlRuleRepositoryAdapter`

**Type**: Outbound Repository Adapter (Spring `@Repository`)  
**Package**: `com.decisionfabric.adapter.outbound.persistence.rule`  
**Implements**: `RuleRepositoryPort`  

**Responsibilities**:
- CRUD and paginated queries for `Rule` and `RuleSet` aggregates
- Pessimistic lock acquisition for write operations
- Domain ↔ JPA entity mapping
- Checking name uniqueness within rule set

**JPA entities**:

| JPA Entity | Table | Notes |
|---|---|---|
| `RuleSetJpaEntity` | `rule_sets` | Simple flat entity |
| `RuleJpaEntity` | `rules` | `@OneToMany(cascade=ALL, fetch=LAZY)` → `RuleVersionJpaEntity` |
| `RuleVersionJpaEntity` | `rule_versions` | Composite PK: `(rule_id, version)` |

**Spring Data repositories**:

| Repository | Key methods |
|---|---|
| `RuleSetJpaRepository extends JpaRepository<RuleSetJpaEntity, UUID>` | `findByName`, `findAllByStatus` |
| `RuleJpaRepository extends JpaRepository<RuleJpaEntity, UUID>` | `findByIdForUpdate` (with `@Lock`), `existsByRuleSetIdAndNameIgnoreCase`, `findAllByRuleSetIdAndStatus` |
| `RuleVersionJpaRepository extends JpaRepository<RuleVersionJpaEntity, RuleVersionId>` | `findAllByRuleIdOrderByVersionAsc`, `findByRuleIdAndVersion` |

---

## Component 5: `DroolsDmnSchemaValidatorAdapter`

**Type**: Outbound Validator Adapter (Spring `@Component`)  
**Package**: `com.decisionfabric.adapter.outbound.dmn`  
**Implements**: `DmnSchemaValidatorPort`  

**Responsibilities**:
- Validate DMN XML against OMG DMN 1.4 schema using Drools `KieServices`
- XXE-safe XML pre-parse before schema validation
- 1 MB size guard
- 1-second timeout wrapper
- Parse-error message extraction

**State**: Stateless singleton — `KieServices` instance created once at startup.

**Reuse**: This component is also used by Unit 3 (`DroolsDmnEngineAdapter`) for decision evaluation. Defined once in Unit 2; wired into Unit 3 via Spring DI.

---

## Component 6: `InMemoryRuleCache`

**Type**: Cache Adapter (Spring `@Component`)  
**Package**: `com.decisionfabric.adapter.outbound.cache`  

**Responsibilities**:
- Maintain heap map of all ACTIVE version DMN XML, keyed by `RuleId`
- Warm up from PostgreSQL on `ApplicationReadyEvent`
- Update on `RuleLifecycleEvent` (post-commit)
- Expose `getActiveVersions(ruleId)` for Unit 3 evaluation path
- Enforce configurable byte cap with oldest-first eviction
- Report metrics and health status

**Internal state**:
```kotlin
private val cache = ConcurrentHashMap<RuleId, List<CachedRuleVersion>>()
private val totalBytes = AtomicLong(0L)
private var startupFailed = false
```

**Configuration injection**:
```kotlin
@Value("\${rule.cache.max-bytes-mb:200}")
private val maxBytesMb: Long
```

**Public API surface** (used by Unit 3):
```kotlin
fun getActiveVersions(ruleId: RuleId): List<CachedRuleVersion>
fun getAllActiveEntries(): Map<RuleId, List<CachedRuleVersion>>
```

**Listeners**:
- `@EventListener(ApplicationReadyEvent::class)` → warm-up
- `@TransactionalEventListener(phase = AFTER_COMMIT)` → per-event update

---

## Component 7: `RuleAuditLogAdapter`

**Type**: Outbound Adapter (Spring `@Component`)  
**Package**: `com.decisionfabric.adapter.outbound.persistence.rule`  

**Responsibilities**:
- Append-only writes to `rule_audit_log` table via `JdbcTemplate`
- Called from `RuleManagementService` after each successful lifecycle transition
- Extracts `userId` from command, `correlationId` from MDC

**No JPA entity** — write-only path; queries are in Unit 5.

**Interface**:
```kotlin
interface RuleAuditPort {
    fun log(ruleId: RuleId, ruleSetId: RuleSetId?, version: Int?, action: RuleAuditAction,
            userId: String, correlationId: String, metadata: Map<String, String> = emptyMap())
}
```

---

## Component 8: `RuleCacheHealthIndicator`

**Type**: Spring Boot Health Indicator (Spring `@Component`)  
**Package**: `com.decisionfabric.adapter.outbound.cache`  
**Implements**: `HealthIndicator`  

**Status thresholds**:
- `DOWN`: startup failed or bytes ≥ maxBytes
- `DEGRADED`: bytes ≥ maxBytes × 0.9
- `UP`: bytes < maxBytes × 0.9

**Details exposed**:
```json
{
  "status": "UP",
  "details": {
    "entries": 42,
    "usageBytes": 18874368,
    "maxBytes": 209715200,
    "usagePct": "9%"
  }
}
```

---

## Component 9: `RuleCacheMetricsRegistrar`

**Type**: Spring `@Component` / `SmartInitializingSingleton`  
**Package**: `com.decisionfabric.adapter.outbound.cache`  

**Responsibilities**:
- Register `rule.cache.size` and `rule.cache.bytes` Gauges with `MeterRegistry` on startup
- Gauges reference live values from `InMemoryRuleCache` without polling

**Pattern**: Gauge registration with live supplier lambda:
```kotlin
Gauge.builder("rule.cache.size") { cache.size.toDouble() }
    .description("Number of rule IDs in the active rule cache")
    .register(meterRegistry)

Gauge.builder("rule.cache.bytes") { cache.totalBytes.get().toDouble() }
    .description("Estimated heap bytes used by InMemoryRuleCache")
    .register(meterRegistry)
```

---

## Component Interaction: Rule Activation Sequence

```
RULE_ADMIN → PUT /activate
     │
     ▼
RuleApiController.activateVersion(id, v)
     │ ActivateVersionCommand
     ▼
RuleManagementService.activateVersion(cmd)
  ┌─ @Transactional(SERIALIZABLE) ─────────────────────────────┐
  │  1. RuleRepositoryPort.findByIdForUpdate(ruleId)            │
  │     → SQL: SELECT ... FOR UPDATE                            │
  │  2. rule.activateVersion(v) → domain validation             │
  │  3. DmnSchemaValidatorPort.validate(dmnXml) → re-validate   │
  │  4. RuleRepositoryPort.save(updatedRule)                    │
  │  5. RuleAuditPort.log(ACTIVATE, ...)                        │
  │  6. publisher.publishEvent(RuleVersionActivated)            │
  └───────────────── DB COMMIT ─────────────────────────────────┘
                          │
                          ▼  (AFTER_COMMIT)
     InMemoryRuleCache.onVersionActivated(event)
          │ load dmnXml from DB
          ▼
          cache.compute(ruleId) { addEntry }
          totalBytes.addAndGet(newEntryBytes)
          evictIfOverCap()
          meterRegistry.counter("rule.lifecycle.events", "action", "ACTIVATED").increment()
     │
     ▼
RuleApiController → 200 OK RuleVersionResponse
```

---

## Flyway Migration: V2 Schema

**File**: `src/main/resources/db/migration/V2__rules_schema.sql`

**Objects created**:
1. `rule_sets` table
2. `rules` table with composite unique index `(rule_set_id, lower(name))`
3. `rule_versions` table with composite PK `(rule_id, version)`
4. `rule_audit_log` table
5. `pg_trgm` extension + GIN index on `rules.name`
6. Composite index `(status, rule_set_id)` on `rules`
7. Index on `rule_versions(rule_id, status)` for active-version queries
8. Index on `rule_audit_log(rule_id, occurred_at)` for future Unit 5 queries

---

## Dependency Graph

```
RuleApiController
  └── RuleManagementUseCase (interface)
        └── RuleManagementService (impl)
              ├── RuleRepositoryPort (interface)
              │     └── PostgreSqlRuleRepositoryAdapter (impl)
              │           ├── RuleJpaRepository
              │           ├── RuleSetJpaRepository
              │           └── RuleVersionJpaRepository
              ├── DmnSchemaValidatorPort (interface)
              │     └── DroolsDmnSchemaValidatorAdapter (impl)
              ├── RuleAuditPort (interface)
              │     └── RuleAuditLogAdapter (impl)
              │           └── JdbcTemplate
              └── ApplicationEventPublisher
                    └── [Spring context event bus]
                          └── InMemoryRuleCache (listener)
                                ├── RuleCacheHealthIndicator
                                └── RuleCacheMetricsRegistrar
```
