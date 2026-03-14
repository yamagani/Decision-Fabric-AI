# Components — Decision-Fabric-AI

> **Architectural Style**: Clean Architecture / Hexagonal (Ports & Adapters)
> **Runtime**: Java/Kotlin Spring Boot on AWS

---

## Architecture Overview

```
+------------------------------------------------------+
|                   INBOUND ADAPTERS                   |
|  DecisionApiController  |  RuleApiController         |
|  AuditApiController     |  ConfigApiController       |
|  ActuatorController                                  |
+--------------------+---------------------------------+
                     |  (calls via Application Services)
+--------------------v---------------------------------+
|               APPLICATION LAYER                      |
|  DecisionEvaluationService  |  RuleManagementService |
|  AuditQueryService          |  SystemConfigService   |
+-----+----------+----------+----------+--------------+
      |          |          |          |
+-----v--+ +----v---+ +----v---+ +----v--------+
| DMN    | | Rule   | | Audit  | | AIProvider  |
| Engine | | Repo   | | Repo   | | Config      |
| Port   | | Port   | | Port   | | Port        |
+-----+--+ +----+---+ +----+---+ +----+--------+
      |          |          |          |
+-----v--+ +----v---+ +----v---+ +----v--------+
|Camunda/| |Postgres| |Postgres| |AWS SSM      |
|Drools  | |Rule    | |Audit   | |Parameter    |
|Adapter | |Adapter | |Adapter | |Store Adapter|
+--------+ +----+---+ +--------+ +-------------+
                |
           +----v---------+
           | In-Memory    |
           | Rule Cache   |
           +--------------+
                     +----------------------------+
                     |   AI AUGMENTATION          |
                     |  LlmProviderPort           |
                     |  LlmProviderAdapter        |
                     |  (AWS Bedrock / OpenAI)    |
                     |  (Circuit Breaker wrapped) |
                     +----------------------------+
```

---

## DOMAIN LAYER

### Component: RuleAggregate

**Package**: `com.decisionfabric.domain.rule`

**Purpose**: Core domain entity representing a versioned DMN business rule with its lifecycle state.

**Responsibilities**:
- Encapsulate rule identity, versioning, DMN XML content, and activation status
- Enforce domain invariants (e.g., cannot delete an active rule)
- Emit domain events on lifecycle transitions (activated, deactivated, deleted)

**Key Entities / Value Objects**:
- `Rule` — aggregate root (ID, name, description, version, status, dmnXml, priority, timestamps)
- `RuleId` — value object (UUID)
- `RuleVersion` — value object (integer, immutable)
- `RuleStatus` — enum (ACTIVE, INACTIVE, DELETED)
- `DmnXmlContent` — value object (validated DMN 1.4 XML string)

---

### Component: RuleSetAggregate

**Package**: `com.decisionfabric.domain.ruleset`

**Purpose**: Groups rules into a named set with independent enable/disable lifecycle.

**Responsibilities**:
- Maintain ordered list of rule references and their evaluation priority
- Enforce set-level enable/disable operations
- Validate that all referenced rules exist before set creation

**Key Entities / Value Objects**:
- `RuleSet` — aggregate root (ID, name, description, status, ordered rule refs)
- `RuleSetStatus` — enum (ENABLED, DISABLED)
- `RuleReference` — value object (ruleId, priority order)

---

### Component: DecisionRequest / DecisionResult

**Package**: `com.decisionfabric.domain.decision`

**Purpose**: Immutable value objects representing the input and output of a decision evaluation.

**Responsibilities**:
- Carry structured input data for rule evaluation
- Carry structured outcome (matched rules, confidence, AI flag, explanation)

**Key Value Objects**:
- `DecisionRequest` — (requestId, inputData: Map, correlationId, requestedAt)
- `DecisionResult` — (requestId, outcome, matchedRules, confidenceScore, aiAugmented, aiReasoning, modelUsed, fallbackReason, evaluatedAt)
- `MatchedRule` — (ruleId, ruleName, ruleVersion)

---

## APPLICATION LAYER (Use Cases / Ports)

### Component: DecisionEvaluationService

**Package**: `com.decisionfabric.application.decision`

**Purpose**: Orchestrates the end-to-end decision evaluation flow.

**Responsibilities**:
- Load active rules from cache
- Invoke DMN engine via `DmnEnginePort`
- Apply AI augmentation via `LlmProviderPort` when confidence below threshold
- Handle LLM fallback (provider unavailable / circuit breaker open)
- Persist audit record via `AuditRepositoryPort`
- Return structured `DecisionResult`

---

### Component: RuleManagementService

**Package**: `com.decisionfabric.application.rule`

**Purpose**: Orchestrates all rule and rule-set lifecycle operations.

**Responsibilities**:
- CRUD operations on `RuleAggregate` via `RuleRepositoryPort`
- Validate DMN XML schema before persist (`DmnSchemaValidatorPort`)
- Invalidate in-memory rule cache on every mutating operation
- Coordinate import (parse `.dmn` → create rules atomically) and export (serialize rules → `.dmn` XML)
- Manage `RuleSetAggregate` lifecycle

---

### Component: AuditQueryService

**Package**: `com.decisionfabric.application.audit`

**Purpose**: Handles queries against the decision audit log.

**Responsibilities**:
- Query by requestId
- Query by time range with pagination
- Filter by `aiAugmented` flag
- Return paginated `DecisionResult` records

---

### Component: SystemConfigService

**Package**: `com.decisionfabric.application.config`

**Purpose**: Manages AI provider configuration at runtime.

**Responsibilities**:
- Read current config from `AiProviderConfigPort`
- Validate config values (threshold range, non-null endpoint)
- Persist updated config (triggers Parameter Store write or DB update)
- Notify `DecisionEvaluationService` of config changes (Spring `ApplicationEvent`)

---

## PORTS (Interfaces — Domain/Application layer)

| Port | Package | Purpose |
|---|---|---|
| `DmnEnginePort` | `application.ports.out` | Evaluate DMN decision tables; validate DMN XML |
| `RuleRepositoryPort` | `application.ports.out` | Persist and retrieve rule aggregates + DMN XML |
| `AuditRepositoryPort` | `application.ports.out` | Append and query decision audit records |
| `LlmProviderPort` | `application.ports.out` | Call external LLM API for AI augmentation |
| `AiProviderConfigPort` | `application.ports.out` | Read/write AI provider configuration |
| `DmnSchemaValidatorPort` | `application.ports.out` | Validate `.dmn` XML against DMN 1.4 schema |

---

## INBOUND ADAPTERS

### Component: DecisionApiController

**Package**: `com.decisionfabric.adapter.inbound.rest.decision`

**Purpose**: REST controller for decision evaluation endpoints.

**Responsibilities**:
- Accept `POST /decisions/evaluate` — validate request, delegate to `DecisionEvaluationService`
- Accept `GET /decisions/{requestId}` and `GET /decisions` — delegate to `AuditQueryService`
- Map domain results to REST response DTOs
- Apply input validation (Bean Validation)
- Enforce authentication/RBAC via Spring Security filter

---

### Component: RuleApiController

**Package**: `com.decisionfabric.adapter.inbound.rest.rule`

**Purpose**: REST controller for rule and rule-set management endpoints.

**Responsibilities**:
- Accept `POST/PUT/DELETE /rules`, `/rules/{id}/activate`, `/rules/{id}/deactivate`
- Accept `POST /rules/import`, `GET /rules/export`
- Accept `POST/PUT/DELETE /rule-sets`
- Delegate all operations to `RuleManagementService`

---

### Component: ConfigApiController

**Package**: `com.decisionfabric.adapter.inbound.rest.config`

**Purpose**: REST controller for AI provider configuration management.

**Responsibilities**:
- Accept `PUT /config/ai-provider` and `PUT /config/ai-provider/credentials`
- Validate input, delegate to `SystemConfigService`
- Mask credential values in responses

---

### Component: ActuatorController (Spring Boot Actuator)

**Package**: Spring Boot Actuator (auto-configured)

**Purpose**: Exposes liveness, readiness, health, and metrics endpoints.

**Responsibilities**:
- `/actuator/health/liveness` — process health (unauthenticated)
- `/actuator/health/readiness` — DB + dependency health (unauthenticated)
- `/actuator/metrics` — Micrometer metrics (authenticated, `system-admin` role)

---

## OUTBOUND ADAPTERS

### Component: PluggableDmnEngineAdapter

**Package**: `com.decisionfabric.adapter.outbound.dmn`

**Purpose**: Pluggable implementation of `DmnEnginePort`. Selectable via `dmn.engine.provider` config property (`camunda` or `drools`).

**Responsibilities**:
- Load compiled DMN model from `DmnXmlContent`
- Execute evaluation for a given input map; return results with hit-policy applied
- Validate DMN 1.4 XML schema via `DmnSchemaValidatorPort`

**Implementations**:
- `CamundaDmnEngineAdapter` — wraps Camunda DMN Engine library
- `DroolsDmnEngineAdapter` — wraps Drools DMN library

---

### Component: PostgreSqlRuleRepositoryAdapter

**Package**: `com.decisionfabric.adapter.outbound.persistence.rule`

**Purpose**: Implements `RuleRepositoryPort` using PostgreSQL (Spring Data JPA).

**Responsibilities**:
- Persist rule metadata + DMN XML in `rules` table
- Persist rule versions in `rule_versions` table
- Persist rule sets in `rule_sets` table
- Read ACTIVE rules for cache population

---

### Component: InMemoryRuleCache

**Package**: `com.decisionfabric.adapter.outbound.cache`

**Purpose**: In-memory cache of active rules for high-throughput evaluation hot path.

**Responsibilities**:
- Load all ACTIVE rules from `RuleRepositoryPort` at startup
- Invalidate and reload on rule lifecycle events (activate/deactivate/update)
- Serve active rules to `DecisionEvaluationService` without DB round-trip

---

### Component: PostgreSqlAuditRepositoryAdapter

**Package**: `com.decisionfabric.adapter.outbound.persistence.audit`

**Purpose**: Implements `AuditRepositoryPort` using PostgreSQL.

**Responsibilities**:
- Append decision audit records to `decision_audit` table
- Query by requestId, time range, aiAugmented flag
- Support pagination

---

### Component: LlmProviderAdapter

**Package**: `com.decisionfabric.adapter.outbound.ai`

**Purpose**: Implements `LlmProviderPort`. Calls external LLM API (AWS Bedrock or OpenAI) via HTTP.

**Responsibilities**:
- Build LLM prompt from `DecisionRequest` and low-confidence DMN context
- Call configured provider endpoint with auth credentials
- Parse LLM response into AI outcome and reasoning string
- Wrap calls in Resilience4j circuit breaker
- Apply configurable timeout; throw `AiProviderUnavailableException` on failure

---

### Component: AwsParameterStoreConfigAdapter

**Package**: `com.decisionfabric.adapter.outbound.config`

**Purpose**: Implements `AiProviderConfigPort` using AWS SSM Parameter Store.

**Responsibilities**:
- Read AI provider configuration from SSM on startup and on polling interval
- Write updated configuration to SSM Parameter Store
- Retrieve credentials from AWS Secrets Manager (not Parameter Store) for sensitive values
- Publish `AiProviderConfigChangedEvent` when polled config differs from cached

---

### Component: ObservabilityAdapter

**Package**: `com.decisionfabric.adapter.outbound.observability`

**Purpose**: Provides structured logging and metrics emission.

**Responsibilities**:
- Configure SLF4J + Logback JSON encoder for structured log output
- Register Micrometer metrics: `decision.requests.total`, `decision.latency`, `ai.invocations.total`, `ai.fallback.total`, `circuit.breaker.state`
- Inject `correlationId` into MDC for all request-scoped log entries
- Ensure no sensitive fields written to logs (reviewed via log filter)
