# Unit 1: Foundation — Code Generation Summary

## Status: Complete

---

## Generated Files

### Build & Configuration
| File | Purpose |
|---|---|
| `build.gradle.kts` | Gradle Kotlin DSL build — all dependencies declared |
| `settings.gradle.kts` | Project name: `decision-fabric-ai` |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 8.6 wrapper |
| `src/main/resources/application.yml` | Base application config |
| `src/main/resources/application-local.yml` | Local dev overrides (TestContainers DB) |
| `src/test/resources/application-test.yml` | Test profile config |

### Domain Base Types (`com.decisionfabric.domain.shared`)
| File | Purpose |
|---|---|
| `DomainEntity.kt` | Abstract base with `id`-based equality |
| `AggregateRoot.kt` | Extends `DomainEntity` — manages domain event collection |
| `ValueObject.kt` | Marker interface |
| `DomainEvent.kt` | Interface with `occurredAt: Instant` |
| `DomainException.kt` | Sealed class hierarchy: `EntityNotFoundException`, `ValidationException`, `BusinessRuleViolationException`, `ConflictException`, `AiUnavailableException`, `DmnValidationException` |

### Outbound Port Interfaces (`com.decisionfabric.application.ports.out`)
| File | Port | Implemented By (future units) |
|---|---|---|
| `DmnEnginePort.kt` | DMN evaluation + schema validation | Unit 3: `DroolsDmnEngineAdapter` |
| `RuleRepositoryPort.kt` | Rule persistence (CRUD + active list) | Unit 2: `PostgreSqlRuleRepositoryAdapter` |
| `AuditRepositoryPort.kt` | Audit record append + query | Unit 3: `PostgreSqlAuditRepositoryAdapter` |
| `LlmProviderPort.kt` | AI augmentation call | Unit 4: `LlmProviderAdapter` |
| `AiProviderConfigPort.kt` | AI config read/write | Unit 4: `AwsParameterStoreConfigAdapter` |
| `DmnSchemaValidatorPort.kt` | OMG DMN 1.4 schema validation | Unit 2: `DroolsDmnSchemaValidatorAdapter` |

### Use-Case Interfaces (`com.decisionfabric.application.ports.in`)
| File | Use Case | Implemented By (future units) |
|---|---|---|
| `DecisionEvaluationUseCase.kt` | evaluate() | Unit 3: `DecisionEvaluationService` |
| `RuleManagementUseCase.kt` | CRUD, activate, import, export | Unit 2: `RuleManagementService` |
| `AuditQueryUseCase.kt` | getDecision(), listDecisions() | Unit 5: `AuditQueryService` |
| `SystemConfigUseCase.kt` | getAiConfig(), updateAiConfig() | Unit 4: `SystemConfigService` |

### Security (`com.decisionfabric.adapter.inbound.security`)
| File | Purpose |
|---|---|
| `SecurityConfig.kt` | `SecurityFilterChain` — JWT OIDC resource server, RBAC endpoint rules, stateless sessions |
| `JwtProperties.kt` | `@ConfigurationProperties` for JWT issuer/JWKS URI |

Role → endpoint mapping:
- `DECISION_CONSUMER` → `POST /api/v1/decisions/**`
- `RULE_READER` → `GET /api/v1/rules/**`
- `RULE_ADMIN` → all `POST/PUT/DELETE /api/v1/rules/**`
- `AUDIT_READER` → `GET /api/v1/decisions/history/**`
- `SYSTEM_ADMIN` → `/api/v1/config/**`, all above

### Cross-Cutting (`com.decisionfabric.adapter.inbound.rest.shared`)
| File | Purpose |
|---|---|
| `CorrelationIdFilter.kt` | `OncePerRequestFilter` — reads/generates `X-Correlation-ID`, writes to MDC + response header |
| `GlobalExceptionHandler.kt` | `@ControllerAdvice` — domain exceptions → HTTP status codes |
| `ErrorResponse.kt` | Standard error DTO: `timestamp`, `status`, `error`, `message`, `correlationId`, `path` |

### Infrastructure
| File | Purpose |
|---|---|
| `db/migration/V1__baseline.sql` | Flyway empty baseline |
| `logback-spring.xml` | JSON structured logs (production); human-readable (local/test) |
| `ObservabilityConfig.kt` | Micrometer common tags: `service`, `env` |
| `openapi/security-scheme.yml` | OpenAPI Bearer JWT security scheme |

### Tests
| File | Coverage |
|---|---|
| `JwtAuthenticationFilterTest.kt` | JWT auth, RBAC enforcement, unauthenticated 401 |
| `CorrelationIdFilterTest.kt` | Header propagation, auto-generation, MDC cleanup |
| `GlobalExceptionHandlerTest.kt` | Exception → HTTP status mapping, error response shape |
| `DecisionFabricAiApplicationTest.kt` | Spring context smoke test (`@SpringBootTest`) |

---

## Key Decisions
| Decision | Value | Rationale |
|---|---|---|
| Spring Boot version | 3.2.3 | Current stable; Java 21 support |
| Kotlin version | 1.9.22 | Stable; full Spring Boot 3 support |
| Java target | 21 | LTS; virtual threads available if needed |
| JWT validation | Spring Security OAuth2 Resource Server (JWKS) | Zero custom JWT parsing code; JWKS auto-rotation |
| Roles claim | `roles` JWT claim → `ROLE_` prefix | Matches standard Spring Security convention |
| Logging | Logback JSON (production), human-readable (local/test) via spring profiles | CloudWatch compatible; developer friendly locally |
| Flyway | Baseline V1 only | V2+ added by subsequent units |

---

## Notes for Unit 2 (Rule Management)
- Implement `RuleRepositoryPort` → `PostgreSqlRuleRepositoryAdapter`
- Implement `DmnSchemaValidatorPort` → `DroolsDmnSchemaValidatorAdapter`
- All domain events must implement `DomainEvent` interface (from this unit)
- Use `AggregateRoot.registerEvent()` to publish `RuleLifecycleEvent`
- Error types to use: `EntityNotFoundException`, `ValidationException`, `DmnValidationException`, `ConflictException` (all from `DomainException.kt`)
- Add Flyway migration `V2__rules_schema.sql`
