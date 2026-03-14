# Unit of Work Dependency — Decision-Fabric-AI

## Deployment Context
Single Spring Boot application — all units share one JVM process and Spring `ApplicationContext`. Dependencies are compile-time and runtime code dependencies, not network calls.

---

## Dependency Matrix

| Unit | Depends On | Type |
|---|---|---|
| Unit 1: Foundation | — | Root unit |
| Unit 2: Rule Management | Unit 1 | Port interfaces, security config, Flyway baseline, base domain types |
| Unit 3: Decision Evaluation | Unit 1, Unit 2 | Port interfaces, `InMemoryRuleCache`, `RuleLifecycleEvent`, Flyway migrations V1+V2 |
| Unit 4: AI Augmentation | Unit 1, Unit 3 | `DecisionEvaluationService` extension point, `AiProviderConfigPort`, port interfaces |
| Unit 5: Audit Query & Operations | Unit 1, Unit 3 | `AuditRepositoryPort`, `PostgreSqlAuditRepositoryAdapter`, Flyway migrations V1+V2+V3 |

---

## Dependency Graph

```
Unit 1: Foundation
├── Unit 2: Rule Management
│   └── Unit 3: Decision Evaluation
│       ├── Unit 4: AI Augmentation & Config
│       └── Unit 5: Audit Query & Operations
```

> Units 4 and 5 both depend on Unit 3 but are **independent of each other** — they can be built in either order after Unit 3 is complete.

---

## Cross-Unit Interfaces

### Unit 1 → Unit 2, 3, 4, 5 (provides)
| Interface | Consuming Units |
|---|---|
| `RuleRepositoryPort` | Unit 2 (implementation), Unit 3 (InMemoryRuleCache reload) |
| `AuditRepositoryPort` | Unit 3 (implementation), Unit 5 (query) |
| `DmnEnginePort` | Unit 3 (implementation) |
| `LlmProviderPort` | Unit 4 (implementation) |
| `AiProviderConfigPort` | Unit 4 (implementation) |
| `DmnSchemaValidatorPort` | Unit 2 (implementation) |
| `DecisionEvaluationUseCase` | Unit 3 (implementation) |
| `RuleManagementUseCase` | Unit 2 (implementation) |
| `AuditQueryUseCase` | Unit 5 (implementation) |
| `SystemConfigUseCase` | Unit 4 (implementation) |
| Spring Security (JWT RBAC) | All units (cross-cutting filter chain) |
| MDC correlation ID filter | All units (cross-cutting) |
| Global exception handler | All units (cross-cutting) |
| Flyway baseline `V1__baseline_schema.sql` | Units 2–5 (additive migrations build on this) |

### Unit 2 → Unit 3 (provides)
| Interface | Description |
|---|---|
| `InMemoryRuleCache` | Unit 3's `DecisionEvaluationService` reads active rules from the cache |
| `RuleLifecycleEvent` | Unit 3's `InMemoryRuleCache` listens to events from Unit 2's `RuleManagementService` |
| `Rule` domain entity | Used in Unit 3's `DecisionEvaluationService` to load DMN XML content |
| Flyway `V2__rules_schema.sql` | Unit 3 Flyway migration `V3__audit_schema.sql` is applied after V2 |

### Unit 3 → Unit 4 (provides)
| Interface | Description |
|---|---|
| `DecisionEvaluationService` (extension point) | Unit 4 extends the AI augmentation path — confidence check + `LlmProviderPort.augmentDecision()` wired in |
| `AuditRepositoryPort` | Unit 4 augmentation results are included in the audit record written by Unit 3 |
| Flyway `V3__audit_schema.sql` | Audit table already exists when Unit 4 adds AI result columns (if needed) |

### Unit 3 → Unit 5 (provides)
| Interface | Description |
|---|---|
| `PostgreSqlAuditRepositoryAdapter` | Unit 5's `AuditQueryService` uses the same `AuditRepositoryPort` implementation |
| `decision_audit` table | Already populated by Unit 3; Unit 5 adds query endpoints on top |

---

## Flyway Migration Dependency Chain

```
V1__baseline_schema.sql          (Unit 1 — empty baseline)
V2__rules_schema.sql             (Unit 2 — rules, rule_sets, rule_versions tables)
V3__audit_schema.sql             (Unit 3 — decision_audit table)
```

> Migrations are applied in version order by Flyway at startup. Later units add new migration scripts but never modify earlier ones.

---

## Build Dependency Order

Since this is a single Gradle project, all units compile together. The dependency order matters for development sequencing:

```
1. Unit 1 → compile & test green (ports, configs, base types)
2. Unit 2 → compile & test green (Rule Management feature slice complete)
3. Unit 3 → compile & test green (Decision Evaluation feature slice complete)
4. Unit 4 + Unit 5 → can proceed in any order (both depend only on Unit 3)
```

---

## External Runtime Dependencies

| Dependency | Used By | Notes |
|---|---|---|
| PostgreSQL | Units 2, 3, 5 | AWS RDS; credentials from AWS Secrets Manager |
| AWS SSM Parameter Store | Unit 4 | AI provider config polling |
| AWS Secrets Manager | Unit 1 (config), Unit 4 | DB credentials, LLM API keys |
| Drools DMN runtime | Units 2, 3 | In-process library; no network call |
| LLM API endpoint (Bedrock / OpenAI) | Unit 4 | External; protected by Resilience4j circuit breaker |
