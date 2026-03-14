# Code Generation Plan — Unit 2: Rule Management

## Unit Context
**Unit**: Unit 2 — Rule Management  
**Stories**: US-1.1 (Create/Update DMN Rule), US-1.2 (Activate Rule Version), US-1.3 (Import/Export DMN File)  
**FRs**: FR-01 (Rule CRUD), FR-02 (Rule Versioning), FR-09 (DMN Compliance)  
**Depends On**: Unit 1 (Foundation) ✅ complete  

## Code Location
**Application Code Root**: `/Users/madhuy/Developer/Decision-Fabric-AI/src/`  
**Package Root**: `com.decisionfabric`  
**Documentation**: `aidlc-docs/construction/unit-2-rule-management/code/`

## Design Artifacts
- Functional Design: `aidlc-docs/construction/unit-2-rule-management/functional-design/`
- NFR Requirements: `aidlc-docs/construction/unit-2-rule-management/nfr-requirements/`
- NFR Design: `aidlc-docs/construction/unit-2-rule-management/nfr-design/`

---

## Execution Checklist

### DOMAIN LAYER

- [x] Step 1: `RuleId.kt` — value class wrapping UUID (`com.decisionfabric.domain.rule`)
- [x] Step 2: `RuleSetId.kt` — value class wrapping UUID
- [x] Step 3: `DmnXmlContent.kt` — inline value class with non-blank + starts-with-`<` validation
- [x] Step 4: `RuleStatus.kt` — enum `{ ACTIVE, INACTIVE }`
- [x] Step 5: `RuleSetStatus.kt` — enum `{ ACTIVE, INACTIVE }`
- [x] Step 6: `RuleVersionStatus.kt` — enum `{ DRAFT, ACTIVE, INACTIVE }`
- [x] Step 7: `RuleSet.kt` — aggregate root with `deactivate()` method
- [x] Step 8: `RuleVersion.kt` — entity (child of `Rule`) with status, dmnXml, activatedAt/By
- [x] Step 9: `Rule.kt` — aggregate root with `addVersion()`, `activateVersion()`, `deactivateVersion()`, `discardVersion()`, `softDelete()`, helper methods
- [x] Step 10: `RuleReference.kt` — lightweight read projection value object
- [x] Step 11: `RuleLifecycleEvent.kt` — sealed class with 6 subtypes (`RuleCreated`, `RuleVersionCreated`, `RuleVersionActivated`, `RuleVersionDeactivated`, `RuleDeleted`, `RuleImported`)

### APPLICATION LAYER — COMMANDS & QUERIES

- [x] Step 12: Command classes (`CreateRuleCommand`, `UpdateRuleCommand`, `ActivateVersionCommand`, `DeactivateVersionCommand`, `DiscardVersionCommand`, `DeleteRuleCommand`, `ImportDmnCommand`, `CreateRuleSetCommand`, `DeleteRuleSetCommand`, `ValidateDmnCommand`) — in `com.decisionfabric.application.rule.command`
- [x] Step 13: Query result views (`RuleSetView`, `RuleView`, `RuleVersionView`, `DmnImportResultView`, `DmnValidationResultView`) — in `com.decisionfabric.application.rule.query`
- [x] Step 14: `RuleManagementUseCase.kt` — **update** existing port interface in `application/ports/in/` to add `createRuleSet`, `deleteRuleSet`, `deactivateVersion`, `discardVersion`, `purgeVersion`, `validateDmn` signatures (the interface was stubbed in Unit 1)

### APPLICATION SERVICE

- [ ] Step 15: `RuleManagementService.kt` — implements `RuleManagementUseCase`; all command handlers with `@Transactional`; pessimistic lock on writes; DMN validation calls; event publishing; MDC enrichment (`com.decisionfabric.application.rule`)

### OUTBOUND ADAPTER — PERSISTENCE

- [ ] Step 16: `RuleSetJpaEntity.kt` — `@Entity`, maps `rule_sets` table (`com.decisionfabric.adapter.outbound.persistence.rule`)
- [ ] Step 17: `RuleJpaEntity.kt` — `@Entity`, `@OneToMany` → `RuleVersionJpaEntity`, maps `rules` table
- [ ] Step 18: `RuleVersionJpaEntity.kt` — `@Entity`, composite PK `(rule_id, version)`, maps `rule_versions` table
- [ ] Step 19: `RuleSetJpaRepository.kt` — `JpaRepository<RuleSetJpaEntity, UUID>` with custom query methods
- [ ] Step 20: `RuleJpaRepository.kt` — `JpaRepository<RuleJpaEntity, UUID>` with `@Lock(PESSIMISTIC_WRITE)` find-for-update and name-uniqueness check
- [ ] Step 21: `RuleVersionJpaRepository.kt` — `JpaRepository` with active-version queries
- [ ] Step 22: `RuleJpaMapper.kt` — bidirectional mapper: `Rule ↔ RuleJpaEntity`, `RuleSet ↔ RuleSetJpaEntity`
- [ ] Step 23: `PostgreSqlRuleRepositoryAdapter.kt` — implements `RuleRepositoryPort`; uses mapper + Spring Data repos; implements all port methods

### OUTBOUND ADAPTER — AUDIT LOG

- [ ] Step 24: `RuleAuditAction.kt` — enum (`CREATE`, `VERSION_CREATED`, `ACTIVATE`, `DEACTIVATE`, `DELETE`, `IMPORT`, `PURGE`)
- [ ] Step 25: `RuleAuditPort.kt` — internal port interface for audit log append
- [ ] Step 26: `RuleAuditLogAdapter.kt` — implements `RuleAuditPort`; `JdbcTemplate` INSERT into `rule_audit_log`

### OUTBOUND ADAPTER — DMN VALIDATOR

- [ ] Step 27: `DroolsDmnSchemaValidatorAdapter.kt` — implements `DmnSchemaValidatorPort`; XXE-safe parser; `CompletableFuture` 1s timeout; `KieServices`-based validation; size guard

### OUTBOUND ADAPTER — CACHE

- [ ] Step 28: `CachedRuleVersion.kt` — data class `(ruleId, version, dmnXml, activatedAt, estimatedBytes)`
- [ ] Step 29: `InMemoryRuleCache.kt` — `ConcurrentHashMap`; byte-cap eviction; warm-up on `ApplicationReadyEvent`; `@TransactionalEventListener(AFTER_COMMIT)` listeners; `getActiveVersions()` / `getAllActiveEntries()` API
- [ ] Step 30: `RuleCacheHealthIndicator.kt` — `HealthIndicator`; UP/DEGRADED/DOWN thresholds
- [ ] Step 31: `RuleCacheMetricsRegistrar.kt` — registers `rule.cache.size` and `rule.cache.bytes` gauges

### INBOUND ADAPTER — REST

- [ ] Step 32: Request/Response DTOs (`CreateRuleRequest`, `UpdateRuleRequest`, `RuleResponse`, `RuleVersionResponse`, `RuleListResponse`, `DmnImportResponse`, `DmnValidationResponse`, `CreateRuleSetRequest`, `RuleSetResponse`, `RuleSetListResponse`) — in `com.decisionfabric.adapter.inbound.rest.rule.dto`
- [ ] Step 33: `RuleSetApiController.kt` — `@RestController`, `/api/v1/rule-sets`, CRUD endpoints
- [ ] Step 34: `RuleApiController.kt` — `@RestController`, `/api/v1/rules`, all 11 endpoints including multipart import, export, validate, purge

### SECURITY CONFIG UPDATE

- [ ] Step 35: Update `SecurityConfig.kt` — add path matchers for `/api/v1/rule-sets/**` and refine `/api/v1/rules/**` rules (RULE_READER for GET, RULE_ADMIN for mutations, SYSTEM_ADMIN for purge)

### DATABASE MIGRATION

- [ ] Step 36: `V2__rules_schema.sql` — creates `rule_sets`, `rules`, `rule_versions`, `rule_audit_log` tables; `pg_trgm` extension; all indexes

### CONFIGURATION

- [ ] Step 37: Update `application.yml` — add `rule.cache.max-bytes-mb: 200`, `spring.servlet.multipart.max-file-size: 1MB`, `spring.servlet.multipart.max-request-size: 2MB`

### TEST FIXTURES

- [ ] Step 38: Test fixture DMN files in `src/test/resources/fixtures/dmn/`: `valid-simple-decision.dmn`, `valid-multi-hit-policy.dmn`, `invalid-missing-namespace.dmn`, `invalid-xxe-attempt.dmn`

### DOMAIN UNIT TESTS

- [ ] Step 39: `RuleTest.kt` — unit tests for all `Rule` aggregate domain methods (`addVersion`, `activateVersion`, `deactivateVersion`, `discardVersion`, `softDelete`); transition guards; invariants
- [ ] Step 40: `RuleSetTest.kt` — unit tests for `RuleSet` domain methods
- [ ] Step 41: `DmnXmlContentTest.kt` — value object validation tests

### APPLICATION SERVICE TESTS

- [ ] Step 42: `RuleManagementServiceTest.kt` — MockK-based unit tests for all service command handlers; mock `RuleRepositoryPort`, `DmnSchemaValidatorPort`, `RuleAuditPort`, `ApplicationEventPublisher`

### VALIDATOR INTEGRATION TESTS

- [ ] Step 43: `DroolsDmnSchemaValidatorAdapterTest.kt` — integration tests using real Drools; tests all 4 fixture files; timeout behaviour; XXE rejection

### REPOSITORY INTEGRATION TESTS

- [ ] Step 44: `PostgreSqlRuleRepositoryAdapterTest.kt` — `@Testcontainers` + `@SpringBootTest`; TestContainers PostgreSQL; tests CRUD, pessimistic lock (concurrent thread test), composite uniqueness, `pg_trgm` index queries; Flyway V1+V2 migrations applied

### CONTROLLER SLICE TESTS

- [ ] Step 45: `RuleApiControllerTest.kt` — `@WebMvcTest`; JWT token factory; tests all endpoints for correct HTTP status, role enforcement (401/403), request validation (400/422), domain exception mapping
- [ ] Step 46: `RuleSetApiControllerTest.kt` — same pattern for rule-set endpoints

### CACHE TESTS

- [ ] Step 47: `InMemoryRuleCacheTest.kt` — unit tests for warm-up, byte-cap eviction, concurrent add/read, event handlers, health indicator thresholds

### CODE SUMMARY

- [ ] Step 48: `unit-2-code-summary.md` in `aidlc-docs/construction/unit-2-rule-management/code/`

---

## Story Traceability

| Story | Steps Covered |
|---|---|
| US-1.1: Create and Update DMN Rule | Steps 1–15, 16–26, 27, 32–37, 39–42, 44–46 |
| US-1.2: Activate Rule Version | Steps 9, 11, 15, 29 (cache reload), 39, 42, 44, 47 |
| US-1.3: Import/Export DMN File | Steps 12, 15, 27, 34 (import/export endpoints), 38, 43 |

## Total Steps: 48
