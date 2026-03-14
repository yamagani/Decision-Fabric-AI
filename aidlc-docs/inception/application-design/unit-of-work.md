# Units of Work — Decision-Fabric-AI

## Deployment Model
**Single Spring Boot application** — one deployable JAR (`decision-fabric-ai.jar`) running on AWS ECS/EKS. All units share a single Spring `ApplicationContext` but are structured as logical vertical slices within the `com.decisionfabric` package tree. Each unit has clear boundaries and can be developed and tested independently before integration.

## Construction Sequence
**Feature-slice-first** — Unit 1 (Foundation) is built first to establish the skeleton and shared infrastructure. Units 2–5 are then built sequentially as independent vertical slices.

## DMN Engine
**Drools DMN adapter** (`DroolsDmnEngineAdapter`) is the primary implementation. `CamundaDmnEngineAdapter` follows in a later iteration.

## Database Migrations
**Flyway** — versioned SQL scripts in `src/main/resources/db/migration/`, applied automatically on Spring Boot startup.

---

## Unit Catalog

---

### Unit 1: Foundation

**Purpose**: Establish the Spring Boot project skeleton, shared configuration, security framework, and domain base types. All subsequent units build on this foundation.

**Scope**:
- `build.gradle.kts` — Kotlin + Spring Boot + Flyway + Drools + Resilience4j + Micrometer + AWS SDK dependencies
- `application.yml` / `application-{env}.yml` — base configuration (DB, security, observability)
- Spring Security configuration — JWT filter, `SecurityFilterChain`, role-permission mappings
- Base domain types — `DomainEntity`, `AggregateRoot`, `DomainEvent`, `ValueObject` base interfaces
- All 6 port interfaces — `DmnEnginePort`, `RuleRepositoryPort`, `AuditRepositoryPort`, `LlmProviderPort`, `AiProviderConfigPort`, `DmnSchemaValidatorPort`
- All 4 use-case interfaces — `DecisionEvaluationUseCase`, `RuleManagementUseCase`, `AuditQueryUseCase`, `SystemConfigUseCase`
- Flyway baseline migration — `V1__baseline_schema.sql` (empty baseline, schema created by subsequent units)
- MDC correlation ID filter — request-scoped `correlationId` added to all threads
- Global exception handler — `@ControllerAdvice` with standard error response shape
- Test utilities — `TestContainers` PostgreSQL base, `MockMvc` helpers, JWT test factory

**User Stories Covered**: Foundation (cross-cutting support for all stories)

**Output**: Compiling Spring Boot application that starts cleanly (no business features yet), all ports and use-case interfaces defined, security config in place.

---

### Unit 2: Rule Management Slice

**Purpose**: Full vertical slice for DMN rule and rule-set lifecycle management — create, update, version, activate, deactivate, import, and export DMN rules.

**Scope**:
- Domain: `Rule`, `RuleId`, `RuleVersion`, `RuleStatus`, `DmnXmlContent`, `RuleSet`, `RuleSetId`, `RuleReference`
- Application: `RuleManagementService` (implements `RuleManagementUseCase`), command objects (`CreateRuleCommand`, `UpdateRuleCommand`, `ActivateVersionCommand`, `ImportDmnCommand`)
- Domain events: `RuleLifecycleEvent` (published on create / activate / deactivate)
- Outbound ports implementation:
  - `PostgreSqlRuleRepositoryAdapter` (implements `RuleRepositoryPort`) — Spring Data JPA, `RuleJpaEntity`, `RuleJpaRepository`
  - `DroolsDmnSchemaValidatorAdapter` (implements `DmnSchemaValidatorPort`) — validates `.dmn` XML against OMG DMN 1.4 schema
  - `InMemoryRuleCache` — ConcurrentHashMap, `@EventListener(RuleLifecycleEvent)` for invalidation + reload
- Inbound adapter: `RuleApiController` — REST endpoints for CRUD, versioning, import (multipart), export, DMN validation
- API DTOs: `CreateRuleRequest`, `UpdateRuleRequest`, `RuleResponse`, `RuleListResponse`, `DmnImportResponse`
- Flyway migrations: `V2__rules_schema.sql` (rules table, rule_sets table, rule_versions table)
- Tests: unit tests for domain rules, integration tests for `PostgreSqlRuleRepositoryAdapter` (TestContainers), `@SpringBootTest` slice tests for `RuleApiController`

**User Stories Covered**: US-1.1, US-1.2, US-1.3

**APIs Delivered**:
- `POST /api/v1/rules` — create rule
- `PUT /api/v1/rules/{id}` — update rule
- `POST /api/v1/rules/{id}/versions/{version}/activate` — activate version
- `POST /api/v1/rules/import` — import `.dmn` file
- `GET /api/v1/rules/{id}/export` — export `.dmn` file
- `DELETE /api/v1/rules/{id}` — deactivate rule
- `GET /api/v1/rules` — list rules (paginated)
- `GET /api/v1/rules/{id}` — get rule by ID

**Depends On**: Unit 1 (Foundation)

---

### Unit 3: Decision Evaluation Slice

**Purpose**: Full vertical slice for evaluating decisions using the active rule set and DMN engine. Includes the Drools DMN engine adapter, rule cache integration, and decision audit persistence.

**Scope**:
- Domain: `DecisionRequest`, `DecisionResult`, `MatchedRule`, `DmnEvaluationResult`
- Application: `DecisionEvaluationService` (implements `DecisionEvaluationUseCase`) — cache lookup → DMN eval → audit append → return result (AI augmentation wired but disabled until Unit 4)
- Outbound ports implementation:
  - `DroolsDmnEngineAdapter` (implements `DmnEnginePort`) — Drools DMN runtime, FEEL evaluator, all 7 hit policies, DRDs, chained decisions
  - `PostgreSqlAuditRepositoryAdapter` (implements `AuditRepositoryPort`) — `DecisionAuditJpaEntity`, `decision_audit` table
- Inbound adapter: `DecisionApiController` — REST endpoint for decision evaluation, request/response DTOs
- API DTOs: `EvaluateDecisionRequest`, `DecisionResultResponse`
- Flyway migration: `V3__audit_schema.sql` (decision_audit table)
- Tests: unit tests for `DecisionEvaluationService` (mock ports), integration tests for `DroolsDmnEngineAdapter` with sample `.dmn` files, `@SpringBootTest` tests for decision evaluation end-to-end

**User Stories Covered**: US-2.1, US-2.2 (rule-only path; AI path completed in Unit 4)

**APIs Delivered**:
- `POST /api/v1/decisions/evaluate` — evaluate decision

**Depends On**: Unit 1 (Foundation), Unit 2 (Rule Management — InMemoryRuleCache, active rules)

---

### Unit 4: AI Augmentation & Config Slice

**Purpose**: Full vertical slice for AI-augmented decision evaluation, LLM provider integration, circuit breaker, and dynamic AI configuration management via AWS SSM Parameter Store.

**Scope**:
- Domain: `AiAugmentationResult`, `LlmProviderConfig` value object
- Application: `SystemConfigService` (implements `SystemConfigUseCase`), `AiProviderConfigChangedEvent`
- Outbound ports implementation:
  - `LlmProviderAdapter` (implements `LlmProviderPort`) — OkHttp/WebClient HTTP client, AWS Bedrock + OpenAI support, Resilience4j `@CircuitBreaker`, configurable timeout, fallback to `AiUnavailableException`
  - `AwsParameterStoreConfigAdapter` (implements `AiProviderConfigPort`) — AWS SDK SSM polling (`@Scheduled`), Secrets Manager for API keys, publishes `AiProviderConfigChangedEvent` on change
- Wire AI augmentation into `DecisionEvaluationService` — enable confidence-threshold check → `LlmProviderPort.augmentDecision()` call → fallback handling
- Inbound adapter: `ConfigApiController` — REST endpoints for reading/updating AI provider config
- API DTOs: `AiConfigResponse`, `UpdateAiConfigRequest`
- Resilience4j config: circuit breaker, retry, timeout policies in `application.yml`
- Tests: unit tests for `LlmProviderAdapter` (WireMock), unit tests for circuit breaker fallback, integration tests for `AwsParameterStoreConfigAdapter` (LocalStack), end-to-end AI augmentation path tests

**User Stories Covered**: US-2.2 (AI path), US-3.1, US-3.2, US-3.3

**APIs Delivered**:
- `GET /api/v1/config/ai` — get current AI provider config
- `PUT /api/v1/config/ai` — update AI provider config (triggers SSM update)

**Depends On**: Unit 1 (Foundation), Unit 3 (Decision Evaluation — `DecisionEvaluationService` extended)

---

### Unit 5: Audit Query & Operations

**Purpose**: Full vertical slice for decision history queries, observability wiring, and system health/operations endpoints.

**Scope**:
- Application: `AuditQueryService` (implements `AuditQueryUseCase`) — paginated decision history queries, per-rule history, per-user history, time-range filters
- Inbound adapter: `AuditApiController` / `DecisionApiController` audit endpoints — REST endpoints for audit queries
- API DTOs: `DecisionHistoryResponse`, `AuditQueryRequest`
- `ObservabilityAdapter`:
  - Micrometer metric registration — 6 core metrics (see services.md)
  - SLF4J + Logback JSON encoder config
  - CloudWatch metrics exporter config
- Spring Boot Actuator: health indicators (`/actuator/health`), metrics (`/actuator/metrics`), info endpoint
- `ActuatorController` — custom health checks (DB connectivity, DMN engine readiness, SSM connectivity)
- Tests: unit tests for `AuditQueryService`, `@SpringBootTest` tests for audit REST endpoints, observability config validation

**User Stories Covered**: US-4.1, US-5.1

**APIs Delivered**:
- `GET /api/v1/decisions/history` — paginated decision history (filterable by ruleId, userId, dateRange)
- `GET /api/v1/decisions/history/{decisionId}` — single decision audit record
- `GET /actuator/health` — health check
- `GET /actuator/metrics` — metrics

**Depends On**: Unit 1 (Foundation), Unit 3 (audit repository already persisting records)

---

## Summary

| Unit | Name | Primary FR Coverage | Depends On |
|---|---|---|---|
| 1 | Foundation | — (cross-cutting) | — |
| 2 | Rule Management | FR-01, FR-02, FR-09 | Unit 1 |
| 3 | Decision Evaluation | FR-03, FR-05 | Unit 1, 2 |
| 4 | AI Augmentation & Config | FR-04, FR-08 | Unit 1, 3 |
| 5 | Audit Query & Operations | FR-06, NFR-07 | Unit 1, 3 |

> **Note**: FR-07 (RBAC) is implemented in Unit 1 (security config) and enforced as cross-cutting behaviour across all units.
