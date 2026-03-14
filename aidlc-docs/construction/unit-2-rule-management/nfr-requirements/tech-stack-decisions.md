# Tech Stack Decisions — Unit 2: Rule Management

## Unit Context
**Unit**: Unit 2 — Rule Management  
**Baseline**: All global tech decisions from inception phase apply (Kotlin 1.9.22, Spring Boot 3.2.3, PostgreSQL, Flyway, Drools, Micrometer). This document records Unit 2-specific technology choices.

---

## 1. Persistence Layer

### TD-01: Spring Data JPA with Pessimistic Write Lock
**Decision**: Use Spring Data JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the `rules` repository method that loads a rule before adding a new version.  
**Library**: `spring-boot-starter-data-jpa` (already in `build.gradle.kts`)  
**Implementation**:
```kotlin
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT r FROM RuleJpaEntity r WHERE r.id = :id AND r.status = 'ACTIVE'")
fun findByIdForUpdate(@Param("id") id: UUID): RuleJpaEntity?
```
**Rationale**: Q2=C. Pessimistic locking prevents two concurrent `PUT /rules/{id}` from computing the same `nextVersionNumber()` and creating duplicate version numbers. Rule management writes are low-frequency (administrative path), so blocking overhead is negligible.  
**Isolation Level**: `@Transactional(isolation = Isolation.SERIALIZABLE)` on `RuleManagementService` write methods.

### TD-02: Separate JPA Entities per Table
**Decision**: Three distinct JPA entity classes map to three tables:
- `RuleSetJpaEntity` → `rule_sets`
- `RuleJpaEntity` → `rules`
- `RuleVersionJpaEntity` → `rule_versions`

`RuleJpaEntity` has a `@OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)` relationship to `RuleVersionJpaEntity`.  
**Rationale**: Keeps JPA entities thin (infrastructure concern); domain aggregates are assembled from JPA entities by the repository adapter.

### TD-03: Audit Log — JDBC Template (Not JPA)
**Decision**: The `rule_audit_log` table is written via Spring `JdbcTemplate` directly, not through JPA.  
**Rationale**: Audit log is append-only and never queried by Unit 2 (queries are in Unit 5). Using `JdbcTemplate` avoids introducing a JPA managed entity for a write-only table and keeps the insert lean.

### TD-04: PostgreSQL `pg_trgm` Extension for Name Search
**Decision**: Enable `pg_trgm` PostgreSQL extension and create a trigram GIN index on `rules.name` to support partial name search (`?search=`).  
**Migration**: Included in `V2__rules_schema.sql`:
```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_rules_name_trgm ON rules USING GIN (name gin_trgm_ops);
CREATE INDEX idx_rules_status_ruleset ON rules (status, rule_set_id);
```
**Rationale**: Q5=A+B. Standard composite index serves filtered list queries; trigram index serves the `?search=partialName` use case without full-text search infrastructure overhead.

---

## 2. Concurrency & Cache Layer

### TD-05: `InMemoryRuleCache` — `ConcurrentHashMap` with No Read Lock
**Decision**: Implement as `ConcurrentHashMap<RuleId, List<CachedRuleVersion>>`. Cache writes use `compute()` for atomic entry replacement. No `ReentrantReadWriteLock`.  
**Rationale**: Q1=A. `ConcurrentHashMap.compute()` provides per-key atomicity sufficient for entry-level updates. Readers on the evaluation hot path see either the old list or the new list — never a partially-constructed state. The brief window of stale reads (< 10ms) is acceptable.  

**Cache entry structure**:
```kotlin
data class CachedRuleVersion(
    val version: Int,
    val dmnXml: DmnXmlContent,
    val activatedAt: Instant,
    val estimatedBytes: Long    // used for byte-cap accounting
)
```

### TD-06: Byte-Cap Eviction for `InMemoryRuleCache`
**Decision**: Track total estimated bytes (UTF-8 string length × 2 as conservative estimate for Kotlin/JVM `String`). When a new entry would exceed `rule.cache.max-bytes-mb × 1_048_576` bytes, evict the single cached entry with the oldest `activatedAt` timestamp.  
**Configuration property**:
```yaml
rule:
  cache:
    max-bytes-mb: 200
```
**Rationale**: Q4=C. Byte-based bounding is more accurate than entry count for variable-size DMN XML payloads.

---

## 3. DMN Validation

### TD-07: Drools `KieServices` for DMN Schema Validation
**Decision**: Implement `DroolsDmnSchemaValidatorAdapter` using the Drools `KieServices` / `KieContainer` API to parse and validate DMN XML. Use `DMNRuntime` for structural validation.  
**Libraries**: `kie-api`, `kie-dmn-core` (already in `build.gradle.kts`)  
**XXE Safety**: Create `XMLInputFactory` with:
```kotlin
factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
```
**Rationale**: OWASP XXE mitigation (SEC-01). Drools DMN validator is reused from Unit 3 (will be the same bean) — defined once in Unit 2.

### TD-08: DMN Validation Timeout via `CompletableFuture`
**Decision**: Wrap the Drools validation call in a `CompletableFuture.supplyAsync()` with a 1-second timeout:
```kotlin
CompletableFuture.supplyAsync { droolsValidate(dmnXml) }
    .get(1, TimeUnit.SECONDS)
```
On `TimeoutException`, throw `DmnValidationException("DMN validation timed out after 1 second")`.  
**Rationale**: PERF-03. Prevents a pathological DMN file from blocking a request thread indefinitely.

---

## 4. REST API Layer

### TD-09: Spring MVC `@RestController` for Rule APIs
**Decision**: `RuleApiController` and `RuleSetApiController` use standard Spring MVC `@RestController` with `@RequestMapping`. Multipart file upload uses `@RequestPart`.  
**Validation**: Jakarta Bean Validation (`@Valid`, `@NotBlank`, `@Size`) on request DTOs.  
**Exception Translation**: `GlobalExceptionHandler` (Unit 1) maps domain exceptions to HTTP statuses; no per-controller `@ExceptionHandler`.

### TD-10: Multipart Upload — Spring `MultipartFile`
**Decision**: DMN import endpoint uses `@RequestPart("file") file: MultipartFile`.  
**Max file size**: Configured globally in `application.yml`:
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 1MB
      max-request-size: 2MB
```
**Rationale**: Size limit enforced before the file reaches application code (SEC-01, BR-DI-02).

### TD-11: DMN Export — `ResponseEntity<ByteArray>`
**Decision**: Export endpoint returns `ResponseEntity<ByteArray>` with:
```
Content-Type: application/xml
Content-Disposition: attachment; filename="{ruleName}-v{version}.dmn"
```
**Rationale**: Forces browser download; correct MIME type for DMN XML consumers.

---

## 5. Event Publishing

### TD-12: Spring `ApplicationEventPublisher` for `RuleLifecycleEvent`
**Decision**: Use Spring's built-in `ApplicationEventPublisher.publishEvent()` for `RuleLifecycleEvent` within-process event publishing.  
**Listener**: `InMemoryRuleCache` listens with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.  
**Rationale**: Decouples cache update from the DB transaction. Cache is updated only after the transaction commits — prevents cache from being primed with data that gets rolled back.

---

## 6. Testing Stack

### TD-13: Domain Unit Tests — MockK + JUnit 5
**Decision**: Pure domain/application-service tests use `MockK` for mocking `RuleRepositoryPort`, `DmnSchemaValidatorPort`. No Spring context loaded.  
**Libraries**: `mockk`, `springmockk` (already in `build.gradle.kts`)

### TD-14: Repository Integration Tests — TestContainers PostgreSQL
**Decision**: `PostgreSqlRuleRepositoryAdapterTest` uses `@Testcontainers` with `@Container PostgreSQLContainer`. Flyway migrations (V1, V2) run automatically before each test class.  
**Rationale**: Tests actual SQL (DDL, indexes, constraints, `pg_trgm`) against a real PostgreSQL container.

### TD-15: Controller Slice Tests — `@WebMvcTest` + JWT Test Factory
**Decision**: `RuleApiControllerTest` uses `@WebMvcTest(RuleApiController::class)` with `@Import(SecurityConfig::class)`. JWT tokens generated by the `JwtTestFactory` utility from Unit 1.  
**Rationale**: Tests RBAC enforcement and request validation without a full Spring context.

### TD-16: DMN Validation Test Fixtures
**Decision**: Valid and invalid `.dmn` test files stored in `src/test/resources/fixtures/dmn/`:
- `valid-simple-decision.dmn` — minimal valid OMG DMN 1.4 document
- `valid-multi-hit-policy.dmn` — valid document with COLLECT hit policy
- `invalid-missing-namespace.dmn` — missing required `namespace` attribute
- `invalid-xxe-attempt.dmn` — contains XXE payload to verify it is safely rejected  
**Rationale**: Reproducible, version-controlled test fixtures for DMN validation path.

---

## 7. Dependencies Summary

No new dependencies required beyond what was established in Unit 1 `build.gradle.kts`. All libraries listed below are already declared:

| Library | Version | Purpose |
|---|---|---|
| `spring-boot-starter-data-jpa` | 3.2.3 | JPA + Hibernate ORM |
| `spring-boot-starter-web` | 3.2.3 | REST controllers, multipart |
| `postgresql` | (BOM) | JDBC driver |
| `flyway-core` | (BOM) | DB migrations |
| `kie-api` | 9.44.0.Final | Drools API |
| `kie-dmn-core` | 9.44.0.Final | DMN parser + validator |
| `micrometer-core` | (BOM) | Custom metrics |
| `mockk` | 1.13.10 | Unit test mocking |
| `testcontainers-postgresql` | 1.19.6 | Integration test DB |
