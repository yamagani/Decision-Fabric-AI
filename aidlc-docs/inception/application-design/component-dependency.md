# Component Dependencies — Decision-Fabric-AI

> **Pattern**: Clean Architecture / Hexagonal (dependency rule enforced — outer layers depend on inner; inner layers never depend on outer)

---

## Dependency Rule

```
Inbound Adapters
    --> Application Services (via Use Case interfaces)
        --> Domain Entities / Value Objects
        --> Outbound Ports (interfaces)
            <-- Outbound Adapters (implement ports)
```

Infrastructure / framework details NEVER flow inward to the domain or application layer.

---

## Dependency Matrix

| Component | Depends On |
|---|---|
| `DecisionApiController` | `DecisionEvaluationUseCase`, `AuditQueryUseCase` |
| `RuleApiController` | `RuleManagementUseCase` |
| `ConfigApiController` | `SystemConfigUseCase` |
| `DecisionEvaluationService` | `DmnEnginePort`, `LlmProviderPort`, `AuditRepositoryPort`, `InMemoryRuleCache`, `AiProviderConfigPort` |
| `RuleManagementService` | `RuleRepositoryPort`, `DmnSchemaValidatorPort`, `InMemoryRuleCache`, `ApplicationEventPublisher` |
| `AuditQueryService` | `AuditRepositoryPort` |
| `SystemConfigService` | `AiProviderConfigPort`, `ApplicationEventPublisher` |
| `InMemoryRuleCache` | `RuleRepositoryPort` (for reload), Spring `@EventListener` |
| `PluggableDmnEngineAdapter` (Camunda/Drools) | DMN engine library (Camunda or Drools SDK) |
| `PostgreSqlRuleRepositoryAdapter` | Spring Data JPA, PostgreSQL |
| `PostgreSqlAuditRepositoryAdapter` | Spring Data JPA, PostgreSQL |
| `LlmProviderAdapter` | HTTP client (OkHttp/WebClient), Resilience4j, `AiProviderConfigPort` |
| `AwsParameterStoreConfigAdapter` | AWS SDK (SSM), AWS SDK (Secrets Manager) |
| `ObservabilityAdapter` | Micrometer, Logback, Spring Boot Actuator |

---

## Communication Patterns

### Inbound (REST → Application)
- **Synchronous request/response** via Spring MVC
- Controllers call application services via use-case interfaces
- Bean Validation (`@Valid`) applied on all inbound DTOs before use-case invocation
- Spring Security filter applied before controller methods

### Application → Outbound Ports
- **Synchronous in-process calls** via port interfaces
- Exception types defined in application layer; adapters translate to application exceptions
- No direct coupling to infrastructure libraries in application / domain layers

### AI Augmentation (sync HTTP with circuit breaker)
- `LlmProviderAdapter` makes synchronous HTTP call (OkHttp / Spring WebClient)
- Wrapped in **Resilience4j `@CircuitBreaker`** with:
  - Failure rate threshold: configurable (default 50%)
  - Wait duration in open state: configurable (default 30s)
  - Fallback method: returns `AiUnavailableException` → caught by `DecisionEvaluationService`
- Configurable timeout enforced per call

### Rule Cache Invalidation (event-driven in-process)
- `RuleManagementService` publishes Spring `ApplicationEvent` (in-process, synchronous)
- `InMemoryRuleCache` listens via `@EventListener` and reloads from DB
- No external message broker needed (single-process cache)

### AI Config Refresh (polling)
- `AwsParameterStoreConfigAdapter` uses a `@Scheduled` task polling SSM every N seconds (configurable)
- On change detected: publishes `AiProviderConfigChangedEvent`
- `DecisionEvaluationService` refreshes its cached threshold on event receipt

---

## Data Flow: Decision Evaluation

```
Client
  |  POST /decisions/evaluate {inputData}
  v
DecisionApiController
  |  validate & map to DecisionRequest
  v
DecisionEvaluationService
  |
  +--[1]--> InMemoryRuleCache.getActiveRules()
  |              | (cache hit → List<Rule>)
  |
  +--[2]--> DmnEnginePort.evaluate(dmnXml, inputData)
  |              | (DmnEvaluationResult: matchedRules, confidenceScore, outputs)
  |
  +--[3] (if confidence < threshold)
  |      LlmProviderPort.augmentDecision(request, dmnContext)
  |              | (AiAugmentationResult: outcome, reasoning, confidence)
  |              | [circuit breaker: if OPEN → fallback path]
  |
  +--[4]--> AuditRepositoryPort.append(DecisionResult)
  |              | (async-safe append to decision_audit table)
  |
  v
DecisionResult → DecisionApiController → HTTP 200 response
```

---

## Data Flow: Rule Import

```
Client
  |  POST /rules/import (multipart .dmn file)
  v
RuleApiController
  |  validate content-type, extract file bytes
  v
RuleManagementService
  |
  +--[1]--> DmnSchemaValidatorPort.validateSchema(dmnXml)
  |              | [throws DmnValidationException → HTTP 422 if invalid]
  |
  +--[2]--> Parse DMN decisions from XML
  |
  +--[3]--> RuleRepositoryPort.save(rule) x N  [atomic transaction]
  |
  v
List<Rule> → HTTP 201
```

---

## Data Flow: In-Memory Cache Refresh

```
RuleManagementService
  |  publishEvent(RuleLifecycleEvent)
  v
Spring ApplicationEventPublisher (in-process)
  |
  v
InMemoryRuleCache.onRuleLifecycleEvent()
  |
  +---> RuleRepositoryPort.findAllActive()  [DB read]
  +---> Replace in-memory rule list (thread-safe ConcurrentHashMap / ReadWriteLock)
```

---

## Package Structure (Clean Architecture)

```
com.decisionfabric/
├── domain/
│   ├── rule/
│   │   ├── Rule.kt
│   │   ├── RuleId.kt
│   │   ├── RuleVersion.kt
│   │   ├── RuleStatus.kt
│   │   └── DmnXmlContent.kt
│   ├── ruleset/
│   │   ├── RuleSet.kt
│   │   ├── RuleSetId.kt
│   │   └── RuleReference.kt
│   └── decision/
│       ├── DecisionRequest.kt
│       ├── DecisionResult.kt
│       └── MatchedRule.kt
├── application/
│   ├── decision/
│   │   ├── DecisionEvaluationUseCase.kt
│   │   └── DecisionEvaluationService.kt
│   ├── rule/
│   │   ├── RuleManagementUseCase.kt
│   │   ├── RuleManagementService.kt
│   │   └── commands/  (CreateRuleCommand, UpdateRuleCommand, ...)
│   ├── audit/
│   │   ├── AuditQueryUseCase.kt
│   │   └── AuditQueryService.kt
│   ├── config/
│   │   ├── SystemConfigUseCase.kt
│   │   └── SystemConfigService.kt
│   └── ports/
│       └── out/
│           ├── DmnEnginePort.kt
│           ├── RuleRepositoryPort.kt
│           ├── AuditRepositoryPort.kt
│           ├── LlmProviderPort.kt
│           ├── AiProviderConfigPort.kt
│           └── DmnSchemaValidatorPort.kt
└── adapter/
    ├── inbound/
    │   └── rest/
    │       ├── decision/  (DecisionApiController, DTOs)
    │       ├── rule/      (RuleApiController, DTOs)
    │       └── config/    (ConfigApiController, DTOs)
    └── outbound/
        ├── dmn/           (CamundaDmnEngineAdapter, DroolsDmnEngineAdapter)
        ├── persistence/
        │   ├── rule/      (PostgreSqlRuleRepositoryAdapter, JPA entities)
        │   └── audit/     (PostgreSqlAuditRepositoryAdapter, JPA entities)
        ├── cache/         (InMemoryRuleCache)
        ├── ai/            (LlmProviderAdapter)
        ├── config/        (AwsParameterStoreConfigAdapter)
        └── observability/ (metrics, logging config)
```
