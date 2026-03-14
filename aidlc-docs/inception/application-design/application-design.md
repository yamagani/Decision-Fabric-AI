# Application Design — Decision-Fabric-AI

> Consolidated design summary. Full detail in the referenced artifact files.

---

## Architectural Style

**Clean Architecture / Hexagonal (Ports & Adapters)**

The domain and application layers are fully isolated from frameworks, databases, and third-party SDKs. All external interactions cross a port interface — defined in the application layer and implemented by adapters in the infrastructure layer. This enables the DMN engine, AI provider, and persistence backend to be swapped with zero impact on business logic.

---

## Layer Boundaries

```
┌──────────────────────────────────────────────────┐
│  Inbound Adapters (REST Controllers)              │
│  DecisionApiController | RuleApiController        │
│  ConfigApiController   | ActuatorController       │
├──────────────────────────────────────────────────┤
│  Application Layer (Use Cases / Services)         │
│  DecisionEvaluationService | RuleManagementService│
│  AuditQueryService         | SystemConfigService  │
│                                                   │
│  Outbound Ports (interfaces)                      │
│  DmnEnginePort | RuleRepositoryPort               │
│  AuditRepositoryPort | LlmProviderPort            │
│  AiProviderConfigPort | DmnSchemaValidatorPort    │
├──────────────────────────────────────────────────┤
│  Domain Core                                      │
│  Rule | RuleSet | DecisionRequest | DecisionResult│
├──────────────────────────────────────────────────┤
│  Outbound Adapters (Infrastructure)               │
│  CamundaDmnEngineAdapter / DroolsDmnEngineAdapter │
│  PostgreSqlRuleRepositoryAdapter                  │
│  InMemoryRuleCache                                │
│  PostgreSqlAuditRepositoryAdapter                 │
│  LlmProviderAdapter                               │
│  AwsParameterStoreConfigAdapter                   │
│  ObservabilityAdapter                             │
└──────────────────────────────────────────────────┘
```

---

## Key Architectural Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Architecture style | Clean Architecture / Hexagonal | Isolates business logic; enables DMN engine swap without core changes |
| DMN engine | Pluggable adapter (Camunda or Drools) | Avoids vendor lock-in; selectable via `dmn.engine.provider` config |
| Rule storage | PostgreSQL (persistence) + in-memory cache (hot path) | Durability + p99 < 200ms for rule-only evaluation |
| AI integration | Synchronous HTTP + Resilience4j circuit breaker | Simplest for request/response flow; circuit breaker prevents cascade failure |
| Audit storage | Same PostgreSQL DB, dedicated `decision_audit` table | Avoids second data store; transactional consistency on evaluation path |
| Config management | AWS SSM Parameter Store (polled) | No restart needed; credentials in Secrets Manager; standard AWS pattern |

---

## DMN Support

Per FR-09 / OMG DMN 1.4:

- All 7 hit policies supported: UNIQUE, FIRST, PRIORITY, ANY, COLLECT, RULE ORDER, OUTPUT ORDER
- Decision Requirement Diagrams (DRDs) with chained decision tables
- Expressions: FEEL and S-FEEL
- Import/export: `.dmn` XML (OMG schema)
- Both `CamundaDmnEngineAdapter` and `DroolsDmnEngineAdapter` implement `DmnEnginePort` identically
- `DmnSchemaValidatorPort` validates `.dmn` imports against OMG schema before persisting

---

## AI Augmentation Behaviour

- `DecisionEvaluationService` checks `dmnResult.confidenceScore` against a configurable threshold
- If `confidenceScore < threshold` (or explicitly requested): calls `LlmProviderPort.augmentDecision()`
- Supported providers: AWS Bedrock, OpenAI (configured via `AiProviderConfigPort` → AWS SSM)
- Circuit breaker state: CLOSED → OPEN on repeated failures; fallback returns `AiUnavailableException`
- Service catches exception and returns decision with `aiAugmented=false` (rule-only result)

---

## Security

| Control | Implementation |
|---|---|
| Authentication | JWT (Bearer token) validated by Spring Security filter |
| Role-based access | RBAC: `decision-consumer`, `rule-reader`, `rule-admin`, `audit-reader`, `system-admin` |
| Encryption in transit | TLS enforced on all endpoints |
| Encryption at rest | AWS RDS encryption for PostgreSQL; Secrets Manager for credentials |
| Input validation | Bean Validation on all DTOs; `DmnSchemaValidatorPort` on `.dmn` imports |
| Audit logging | Every decision call appended to `decision_audit` table with user context |
| Secrets | No secrets in config files; all from AWS Secrets Manager |

---

## Observability

- **Metrics**: Micrometer → CloudWatch (6 core metrics — see services.md)
- **Logging**: SLF4J + Logback JSON structured output; MDC correlation ID on every request thread
- **Health**: Spring Boot Actuator (`/actuator/health`, `/actuator/metrics`)
- **Tracing**: MDC `correlationId` propagated through all service calls and included in audit record

---

## Artifact Reference

| Artifact | File |
|---|---|
| Component catalog (all layers) | [components.md](components.md) |
| Kotlin method signatures | [component-methods.md](component-methods.md) |
| Service orchestration flows & RBAC | [services.md](services.md) |
| Dependency matrix & data flow diagrams | [component-dependency.md](component-dependency.md) |

---

## Alignment to Requirements

| Requirement | Design Element |
|---|---|
| FR-01 Rule Management | `RuleManagementService`, `RuleApiController`, `PostgreSqlRuleRepositoryAdapter` |
| FR-02 Rule Versioning | `RuleVersion` value object, version column in rules table, `RuleManagementService.activateVersion()` |
| FR-03 Decision Evaluation | `DecisionEvaluationService`, `DmnEnginePort`, `InMemoryRuleCache` |
| FR-04 AI Augmentation | `LlmProviderPort`, `LlmProviderAdapter`, `AiProviderConfigPort`, circuit breaker fallback |
| FR-05 Audit Trail | `AuditRepositoryPort`, `PostgreSqlAuditRepositoryAdapter`, `decision_audit` table |
| FR-06 History Queries | `AuditQueryService`, `AuditQueryUseCase` |
| FR-07 RBAC | Spring Security, role table in services.md |
| FR-08 AI Config | `SystemConfigService`, `AwsParameterStoreConfigAdapter`, `AiProviderConfigChangedEvent` |
| FR-09 DMN Compliance | `DmnEnginePort`, both engine adapters, `DmnSchemaValidatorPort`, DMN XML import/export |
| NFR-01 Performance | `InMemoryRuleCache` for p99 < 200ms (rule-only), circuit breaker for AI timeout bound |
| NFR-02 Reliability | Resilience4j, multi-AZ deployment, circuit breaker, graceful fallback |
| NFR-03 Security | JWT RBAC, TLS, encryption at rest, Secrets Manager, input validation |
| NFR-04 Scalability | Stateless services (cache per instance), horizontal scaling, PostgreSQL connection pool |
| NFR-05 Auditability | Decision audit table, structured logging, MDC correlation ID |
| NFR-06 Maintainability | Hexagonal architecture, port/adapter isolation, pluggable DMN engine |
| NFR-07 Operability | Spring Boot Actuator, Micrometer+CloudWatch, health checks, structured JSON logs |
