# NFR Design Patterns — Unit 2: Rule Management

## Overview
This document maps each NFR requirement to a concrete design pattern with implementation specifics for Unit 2. Patterns are technology-agnostic where possible; implementation language is Kotlin + Spring Boot 3.x.

---

## Pattern 1: Pessimistic Lock on Rule Aggregate (Concurrency Safety)

**Addresses**: AVAIL-03, PERF-01, TD-01  
**Problem**: Two concurrent `PUT /rules/{id}` requests could both compute the same `nextVersionNumber()` leading to duplicate version numbers and lost updates.  
**Pattern**: **Pessimistic Write Lock** — acquire a database row lock on the `rules` row before reading current versions, increment, and write.

### Design

```
PUT /rules/{id}
       │
       ▼
RuleManagementService.updateRule(cmd)
  @Transactional(isolation = SERIALIZABLE)
       │
       ▼
PostgreSqlRuleRepositoryAdapter.findByIdForUpdate(ruleId)
  ──► SQL: SELECT * FROM rules WHERE id = ? FOR UPDATE
       │         (blocks second concurrent caller until tx commits)
       ▼
Rule.addVersion(newDmnXml)  ◄── safe: version number computed from locked row
       │
       ▼
repository.save(updatedRule)
       │
       ▼
COMMIT ──► lock released ──► second caller unblocks and re-reads
```

**Key invariants enforced by this pattern**:
- Version numbers are strictly monotonically increasing with no gaps
- No two concurrent sessions can produce the same version number for the same rule
- `SERIALIZABLE` isolation prevents phantom reads during the transaction

**Trade-off**: Rule update throughput is serialised per `ruleId`. Acceptable because rule management is a low-frequency administrative path (not on the evaluation hot path).

---

## Pattern 2: Copy-Reference Cache Update (Lock-Free Reads)

**Addresses**: SCALE-04, TD-05, PERF-04  
**Problem**: Cache reads on the evaluation hot path must never block. Writes (activation/deactivation events) must update the cache atomically per key without corrupting concurrent reads.  
**Pattern**: **Single-Key Atomic Swap** using `ConcurrentHashMap.compute()` — each `ruleId` key's value is replaced atomically as a whole immutable `List<CachedRuleVersion>`.

### Design

```
┌──────────────────────────────────────────────────────────┐
│                   InMemoryRuleCache                       │
│                                                           │
│  cache: ConcurrentHashMap<RuleId, List<CachedRuleVersion>>│
│                                                           │
│  ON ACTIVATION EVENT:                                     │
│    cache.compute(ruleId) { _, existing ->                 │
│        val updated = (existing ?: emptyList()) + newEntry │
│        updated  ◄── atomically replaces entry            │
│    }                                                      │
│                                                           │
│  ON DEACTIVATION / DELETE EVENT:                          │
│    cache.compute(ruleId) { _, existing ->                 │
│        existing?.filterNot { it.version == v }           │
│        ◄── null removes the key if list becomes empty    │
│    }                                                      │
│                                                           │
│  ON READ (hot path):                                      │
│    cache[ruleId]  ◄── O(1), never blocks                │
│    may return list from before or after in-flight write  │
│    (stale window < 10ms — acceptable per SCALE-04)       │
└──────────────────────────────────────────────────────────┘
```

**Stale-read window analysis**:
- `compute()` holds a per-segment lock only for the duration of the lambda execution (nanoseconds to microseconds)
- A reader calling `cache[ruleId]` concurrently will see either the old or new list atomically — never a partially-written state
- The brief window of stale data is acceptable: rule management events are infrequent; evaluation correctness is preserved once the cache update completes (< 10ms)

---

## Pattern 3: Byte-Cap Eviction with Oldest-First Policy

**Addresses**: SCALE-02, TD-06  
**Problem**: Unbounded accumulation of ACTIVE rule versions (each up to 1 MB) could exhaust JVM heap.  
**Pattern**: **Bounded Cache with Capacity-Triggered Eviction** — track total estimated bytes; synchronously evict on every write that would breach the cap.

### Design

```
InMemoryRuleCache state:
  totalEstimatedBytes: AtomicLong        ← current heap estimate
  maxBytes: Long                         ← from rule.cache.max-bytes-mb (default 200MB)

ON ACTIVATION (addEntry):
  newEntryBytes = estimateBytes(dmnXml)
  cache.compute(ruleId) { _, existing ->
      val newList = (existing ?: emptyList()) + newEntry
      totalEstimatedBytes.addAndGet(newEntryBytes)

      // Evict if over cap
      while (totalEstimatedBytes.get() > maxBytes) {
          evictOldestEntry()   // removes entry with smallest activatedAt across all ruleIds
          log(WARN, "Cache eviction due to capacity constraint")
          meterRegistry.counter("rule.cache.evictions", "reason", "capacity").increment()
      }
      newList
  }

evictOldestEntry():
  oldest = cache.values.flatten().minByOrNull { it.activatedAt }
  cache.compute(oldest.ruleId) { _, list ->
      list?.filterNot { it.version == oldest.version }
          ?.takeIf { it.isNotEmpty() }   // removes key if empty
  }
  totalEstimatedBytes.addAndGet(-oldest.estimatedBytes)
```

**Byte estimation**: `dmnXml.value.length * 2L` (conservative: Java String is 2 bytes/char on heap, actual overhead may be less with compact strings but this overestimates safely).

---

## Pattern 4: XXE-Safe Parse-Before-Validate (Defence-in-Depth)

**Addresses**: SEC-01, TD-07, TD-08  
**Problem**: User-supplied XML (DMN files and inline XML) is an OWASP Top 10 injection vector (XXE). Additionally, a pathologically complex DMN document could cause a CPU-exhaust denial-of-service in the validator.  
**Pattern**: **Input Sanitisation Pipeline** — size-limit → XXE-safe parse → schema validate → timeout guard.

### Design

```
inbound DMN XML string / file bytes
         │
         ▼
[Stage 1] Size Guard
  if (input.length > 1_048_576) throw ValidationException("DMN file exceeds 1 MB limit")
         │
         ▼
[Stage 2] XXE-Safe XML Pre-Parse
  val factory = XMLInputFactory.newInstance().apply {
      setProperty(IS_SUPPORTING_EXTERNAL_ENTITIES, false)
      setProperty(SUPPORT_DTD, false)
  }
  factory.createXMLStreamReader(StringReader(input))  // parse; throws on malformed XML
         │
         ▼
[Stage 3] DMN Schema Validation (with timeout)
  val future = CompletableFuture.supplyAsync { droolsValidate(input) }
  val result = try {
      future.get(1, TimeUnit.SECONDS)
  } catch (e: TimeoutException) {
      future.cancel(true)
      throw DmnValidationException("DMN validation timed out")
  }
         │
         ▼
  if (!result.isValid) throw DmnValidationException(result.errors)
         │
         ▼
  DmnXmlContent(input)  // safe to construct
```

This pipeline is invoked in `DmnSchemaValidatorPort` (implemented by `DroolsDmnSchemaValidatorAdapter`) and called from `RuleManagementService` before any persist operation.

---

## Pattern 5: Transactional Outbox-Style Cache Update (AFTER_COMMIT)

**Addresses**: TD-12, AVAIL-02  
**Problem**: If the cache is updated *inside* the database transaction and the transaction subsequently rolls back, the cache will hold data that was never committed — a cache poisoning scenario.  
**Pattern**: **Post-Commit Event Listener** — cache update is wired to `TransactionPhase.AFTER_COMMIT` so it fires only after the DB transaction has durably committed.

### Design

```
RuleManagementService.activateVersion(cmd)
  ┌─ @Transactional(SERIALIZABLE) ──────────────────────┐
  │  1. Load rule (pessimistic lock)                     │
  │  2. rule.activateVersion(v)                          │
  │  3. repository.save(rule)                            │
  │  4. auditLog.append(ACTIVATE event)                  │
  │  5. applicationEventPublisher.publishEvent(          │
  │         RuleVersionActivated(ruleId, v, ...)         │
  │     )  ◄── event is queued but NOT dispatched yet   │
  └──────────────────── COMMIT ─────────────────────────┘
                              │
                              ▼
            @TransactionalEventListener(AFTER_COMMIT)
            InMemoryRuleCache.onVersionActivated(event)
              1. load dmnXml from DB (post-commit read)
              2. cache.compute(ruleId) { addEntry }
              3. update totalEstimatedBytes
              4. evict if over cap
```

**Failure scenario**: If the AFTER_COMMIT listener fails (e.g., DB read error), the exception is caught, logged at `ERROR`, and the cache entry is skipped. The cache becomes stale for that entry. This is acceptable — the next evaluation request that misses the cache will re-query the DB (Unit 3 fallback path).

---

## Pattern 6: Repository Abstraction with Domain Mapper

**Addresses**: NFR-06 (Maintainability), TD-02  
**Problem**: JPA entities (`@Entity`) must not leak into the domain layer; domain aggregates must be independent of persistence technology.  
**Pattern**: **Anti-Corruption Layer / Repository Mapper** — the adapter translates between JPA entities and domain objects in both directions.

### Design

```
Domain Layer                    Infrastructure Layer
─────────────────               ──────────────────────────────
RuleRepositoryPort               PostgreSqlRuleRepositoryAdapter
  findById(RuleId): Rule?    ◄──   findByIdForUpdate()
  save(Rule)                 ──►   toJpaEntity(Rule): RuleJpaEntity
  findByRuleSetId(...)            fromJpaEntity(RuleJpaEntity): Rule
  existsByNameInRuleSet(...)      RuleJpaRepository (Spring Data)

Mapper responsibilities:
  toJpaEntity:   Rule → RuleJpaEntity + List<RuleVersionJpaEntity>
  fromJpaEntity: RuleJpaEntity (with eager/lazy loaded versions) → Rule aggregate
  
Value object mapping:
  RuleId ↔ UUID
  RuleSetId ↔ UUID  
  DmnXmlContent ↔ String (column: dmn_xml TEXT)
  RuleStatus ↔ String (column: status VARCHAR(20))
  RuleVersionStatus ↔ String
```

---

## Pattern 7: Structured Observability (MDC + Metrics + Health)

**Addresses**: OBS-01, OBS-02, OBS-03, NFR-07  
**Pattern**: Three-layer observability integrated at the adapter and service boundaries.

### Layer 1 — MDC Context Propagation

```
CorrelationIdFilter (Unit 1)
  MDC.put("correlationId", ...)
         │
         ▼
SecurityConfig JWT filter
  MDC.put("userId", jwt.subject)        ← added in Unit 2 SecurityConfig enhancement
         │
         ▼
RuleManagementService
  val ruleId = ... 
  MDC.put("ruleId", ruleId.value.toString())     ← per-operation
  try { ... } finally { MDC.remove("ruleId") }
```

### Layer 2 — Micrometer Custom Metrics

| Metric | Registration Point | Update Point |
|---|---|---|
| `rule.cache.size` | `@PostConstruct` Gauge | Live (reads `cache.size`) |
| `rule.cache.bytes` | `@PostConstruct` Gauge | Live (reads `totalEstimatedBytes`) |
| `rule.cache.evictions{reason}` | N/A | On each eviction in compute() |
| `rule.lifecycle.events{action}` | N/A | In each RuleLifecycleEvent handler |
| `rule.dmn.validation.duration{result}` | N/A | Timer around `DmnSchemaValidatorPort.validate()` |

### Layer 3 — Health Indicator

```kotlin
// RuleCacheHealthIndicator logic
when {
    cacheFailedOnStartup         -> Health.down().withDetail("reason", "startup failure")
    bytesUsed >= maxBytes        -> Health.down().withDetail("usage", "$bytesUsed/$maxBytes bytes")
    bytesUsed >= maxBytes * 0.9  -> Health.status("DEGRADED")
                                         .withDetail("usage", "$bytesUsed/$maxBytes bytes")
    else                         -> Health.up().withDetail("entries", cache.size)
                                         .withDetail("usage", "$bytesUsed/$maxBytes bytes")
}
```

---

## Pattern 8: Manual Purge — Restricted Delete with Guard

**Addresses**: SEC-05, BR-VDC-01, Q3=C  
**Pattern**: **Guarded Delete** — version purge is a restricted, explicit, point-in-time operation protected by role and status guards.

### Design

```
DELETE /api/v1/rules/{id}/versions/{v}
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
         │
         ▼
RuleManagementService.purgeVersion(ruleId, version)
  1. Load rule (any status — purge works on soft-deleted rules too)
  2. Find version v
  3. Guard: version.status must be INACTIVE
     → if DRAFT or ACTIVE: throw BusinessRuleViolationException(
           "Cannot purge version $v: status is ${version.status}. Only INACTIVE versions may be purged.")
  4. Hard-delete rule_versions row (direct DELETE SQL via JdbcTemplate)
  5. Append rule_audit_log: action=PURGE, version=v, userId, correlationId
  6. Return 204 No Content
```

**No event published**: Purge does not publish a `RuleLifecycleEvent` because purged versions are already `INACTIVE` and therefore not in the cache.
