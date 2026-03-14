# Services — Decision-Fabric-AI

> Application services orchestrate use cases and coordinate between domain objects and outbound ports.
> These are the primary entry points from inbound adapters into the application core.

---

## Service: DecisionEvaluationService

**Package**: `com.decisionfabric.application.decision`
**Spring Bean**: `@Service`
**Implements**: `DecisionEvaluationUseCase`

### Responsibility
Orchestrates the full decision evaluation pipeline for a single request.

### Orchestration Flow

```
DecisionRequest
    |
    v
[1] Load active rules from InMemoryRuleCache
    |
    v
[2] DmnEnginePort.evaluate(dmnXml, inputData)
    |
    +-- confidenceScore >= threshold?
    |       YES --> build DecisionResult (aiAugmented=false)
    |
    +-- confidenceScore < threshold?
            |
            v
          [3] LlmProviderPort.isAvailable()?
                YES --> LlmProviderPort.augmentDecision(...)
                        --> build DecisionResult (aiAugmented=true)
                NO  --> build fallback DecisionResult (aiAugmented=false, fallbackReason set)
    |
    v
[4] AuditRepositoryPort.append(result)
    |
    v
[5] Return DecisionResult
```

### Dependencies (injected via constructor)
| Dependency | Type |
|---|---|
| `dmnEnginePort` | `DmnEnginePort` |
| `llmProviderPort` | `LlmProviderPort` |
| `auditRepositoryPort` | `AuditRepositoryPort` |
| `ruleCache` | `InMemoryRuleCache` |
| `aiProviderConfigPort` | `AiProviderConfigPort` |

### Events Consumed
- `AiProviderConfigChangedEvent` — refreshes cached confidence threshold

---

## Service: RuleManagementService

**Package**: `com.decisionfabric.application.rule`
**Spring Bean**: `@Service`
**Implements**: `RuleManagementUseCase`

### Responsibility
Orchestrates all rule and rule-set lifecycle operations including DMN import/export.

### Orchestration Flows

**Create Rule**:
```
CreateRuleCommand
    --> DmnSchemaValidatorPort.validateSchema(dmnXml)  [throws if invalid]
    --> RuleRepositoryPort.save(new Rule, status=INACTIVE)
    --> (no cache invalidation — rule is inactive)
    --> return Rule
```

**Activate Rule**:
```
RuleId
    --> RuleRepositoryPort.findById(ruleId)
    --> rule.activate()
    --> RuleRepositoryPort.save(rule)
    --> InMemoryRuleCache.invalidateAndReload()
    --> publish RuleLifecycleEvent(ACTIVATED)
    --> return Rule
```

**Import DMN Model** (atomic):
```
DmnXmlContent
    --> DmnSchemaValidatorPort.validateSchema(xml)  [throws DmnValidationException if invalid — no rules created]
    --> Parse DMN decisions from XML
    --> For each decision: RuleRepositoryPort.save(rule, status=INACTIVE)
    --> return List<Rule>
```

**Export DMN Model**:
```
List<RuleId>
    --> RuleRepositoryPort.findById(each id)
    --> Serialize rules to DMN 1.4 XML via DmnEnginePort
    --> return DmnXmlContent
```

### Dependencies
| Dependency | Type |
|---|---|
| `ruleRepositoryPort` | `RuleRepositoryPort` |
| `dmnSchemaValidatorPort` | `DmnSchemaValidatorPort` |
| `ruleCache` | `InMemoryRuleCache` |
| `eventPublisher` | `ApplicationEventPublisher` |

---

## Service: AuditQueryService

**Package**: `com.decisionfabric.application.audit`
**Spring Bean**: `@Service`
**Implements**: `AuditQueryUseCase`

### Responsibility
Handles all read-only queries against the decision audit log.

### Orchestration Flows

**Get by Request ID**:
```
RequestId
    --> AuditRepositoryPort.findByRequestId(id)
    --> throws DecisionNotFoundException if null
    --> return DecisionResult
```

**Query by Time Range**:
```
AuditQuery(from, to, aiAugmented?, pageToken?, limit)
    --> AuditRepositoryPort.query(query)
    --> return Page<DecisionResult> with nextPageToken
```

### Dependencies
| Dependency | Type |
|---|---|
| `auditRepositoryPort` | `AuditRepositoryPort` |

---

## Service: SystemConfigService

**Package**: `com.decisionfabric.application.config`
**Spring Bean**: `@Service`
**Implements**: `SystemConfigUseCase`

### Responsibility
Manages AI provider configuration reads and writes. Notifies evaluation service of changes.

### Orchestration Flows

**Update Config**:
```
UpdateAiProviderConfigCommand
    --> Validate: confidenceThreshold in [0.0, 1.0], endpoint non-null, timeoutMs > 0
    --> AiProviderConfigPort.saveConfig(config)
    --> publish AiProviderConfigChangedEvent
    --> return updated AiProviderConfig
```

**Update Credentials**:
```
UpdateAiProviderCredentialsCommand
    --> AiProviderConfigPort.saveCredentials(credentials)  [stored in Secrets Manager]
    --> return (no credential values in response)
```

### Dependencies
| Dependency | Type |
|---|---|
| `aiProviderConfigPort` | `AiProviderConfigPort` |
| `eventPublisher` | `ApplicationEventPublisher` |

---

## Cross-Cutting Services

### Authentication / Authorization

**Mechanism**: Spring Security with JWT filter chain

| Role | Permissions |
|---|---|
| `decision-consumer` | POST `/decisions/evaluate` |
| `rule-reader` | GET `/rules`, GET `/rules/{id}`, GET `/rules/export` |
| `rule-admin` | All `/rules/**` and `/rule-sets/**` write operations |
| `audit-reader` | GET `/decisions`, GET `/decisions/{id}` |
| `system-admin` | PUT `/config/**`, GET `/actuator/metrics` |
| *(none)* | GET `/actuator/health/**` (public) |

**Implementation**: `JwtAuthenticationFilter` validates JWT, extracts roles, populates `SecurityContext`. Method-level `@PreAuthorize` annotations on controllers.

---

### Observability Service

**Mechanism**: Spring Boot Actuator + Micrometer + Logback JSON

| Metric | Type | Description |
|---|---|---|
| `decision.requests.total` | Counter | Total decision evaluations (tags: outcome, aiAugmented) |
| `decision.latency` | Timer/Histogram | p50/p95/p99 evaluation latency |
| `ai.invocations.total` | Counter | Total LLM API calls attempted |
| `ai.fallback.total` | Counter | Total AI fallbacks (provider unavailable) |
| `decision.errors.total` | Counter | Total evaluation errors (tags: errorType) |
| `circuit.breaker.state` | Gauge | 0=CLOSED, 1=OPEN, 2=HALF_OPEN |

**Logging**: All log entries via SLF4J with MDC fields: `correlationId`, `requestId`, `service`, `timestamp`, `level`.

---

### Domain Events

| Event | Published By | Consumed By |
|---|---|---|
| `RuleLifecycleEvent` (ACTIVATED, DEACTIVATED, DELETED) | `RuleManagementService` | `InMemoryRuleCache` |
| `AiProviderConfigChangedEvent` | `SystemConfigService` | `DecisionEvaluationService` |
