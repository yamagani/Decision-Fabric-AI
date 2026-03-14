# Code Generation Plan — Unit 1: Foundation

## Unit Summary
**Unit**: Unit 1 — Foundation  
**Deployment**: Single Spring Boot application (Kotlin, Spring Boot 3.x, Gradle)  
**Purpose**: Project skeleton, shared configuration, security framework, port/use-case interfaces, base domain types, Flyway baseline, cross-cutting concerns  
**Stages skipped**: Functional Design, NFR Requirements, NFR Design, Infrastructure Design (all defined globally in requirements.md + application-design.md)

---

## Stories Covered
- Cross-cutting support for all stories (FR-07 RBAC, MDC correlation IDs, error handling)

---

## Execution Checklist

### Part 1 — Project Scaffold
- [x] 1.1: Create `build.gradle.kts` with all dependencies (Kotlin, Spring Boot 3.x, Spring Security, Spring Data JPA, PostgreSQL driver, Flyway, Drools BOM, Resilience4j, Micrometer, AWS SDK BOM, OkHttp, Jackson, TestContainers)
- [x] 1.2: Create `settings.gradle.kts` with project name `decision-fabric-ai`
- [x] 1.3: Create `gradle/wrapper/gradle-wrapper.properties`
- [x] 1.4: Create `src/main/kotlin/com/decisionfabric/DecisionFabricAiApplication.kt` — Spring Boot main entry point
- [x] 1.5: Create `src/main/resources/application.yml` — base configuration (datasource, JPA, Flyway, security, Resilience4j, management endpoints, logging)
- [x] 1.6: Create `src/main/resources/application-local.yml` — local dev overrides (TestContainers-compatible DB URL)

### Part 2 — Domain Base Types
- [x] 2.1: Create `src/main/kotlin/com/decisionfabric/domain/shared/DomainEntity.kt`
- [x] 2.2: Create `src/main/kotlin/com/decisionfabric/domain/shared/AggregateRoot.kt`
- [x] 2.3: Create `src/main/kotlin/com/decisionfabric/domain/shared/ValueObject.kt`
- [x] 2.4: Create `src/main/kotlin/com/decisionfabric/domain/shared/DomainEvent.kt`
- [x] 2.5: Create `src/main/kotlin/com/decisionfabric/domain/shared/DomainException.kt` — base sealed class for all domain exceptions

### Part 3 — Outbound Port Interfaces
- [x] 3.1: Create `src/main/kotlin/com/decisionfabric/application/ports/out/DmnEnginePort.kt`
- [x] 3.2: Create `src/main/kotlin/com/decisionfabric/application/ports/out/RuleRepositoryPort.kt`
- [x] 3.3: Create `src/main/kotlin/com/decisionfabric/application/ports/out/AuditRepositoryPort.kt`
- [x] 3.4: Create `src/main/kotlin/com/decisionfabric/application/ports/out/LlmProviderPort.kt`
- [x] 3.5: Create `src/main/kotlin/com/decisionfabric/application/ports/out/AiProviderConfigPort.kt`
- [x] 3.6: Create `src/main/kotlin/com/decisionfabric/application/ports/out/DmnSchemaValidatorPort.kt`

### Part 4 — Use-Case Interfaces (Inbound Ports)
- [x] 4.1: Create `src/main/kotlin/com/decisionfabric/application/ports/in/DecisionEvaluationUseCase.kt`
- [x] 4.2: Create `src/main/kotlin/com/decisionfabric/application/ports/in/RuleManagementUseCase.kt`
- [x] 4.3: Create `src/main/kotlin/com/decisionfabric/application/ports/in/AuditQueryUseCase.kt`
- [x] 4.4: Create `src/main/kotlin/com/decisionfabric/application/ports/in/SystemConfigUseCase.kt`

### Part 5 — Security Configuration
- [x] 5.1: `JwtAuthenticationFilter` — handled natively by Spring Security `oauth2ResourceServer`
- [x] 5.2: Create `src/main/kotlin/com/decisionfabric/adapter/inbound/security/SecurityConfig.kt` — `SecurityFilterChain`, role-permission mappings, CSRF disabled (stateless API), CORS config
- [x] 5.3: Create `src/main/kotlin/com/decisionfabric/adapter/inbound/security/JwtProperties.kt` — `@ConfigurationProperties` for JWT issuer, JWKS URI, audience

### Part 6 — Cross-Cutting Infrastructure
- [x] 6.1: Create `src/main/kotlin/com/decisionfabric/adapter/inbound/rest/shared/CorrelationIdFilter.kt` — servlet filter that reads/generates `X-Correlation-ID`, sets MDC `correlationId`
- [x] 6.2: Create `src/main/kotlin/com/decisionfabric/adapter/inbound/rest/shared/GlobalExceptionHandler.kt` — `@ControllerAdvice`: maps domain exceptions to HTTP status codes, standard `ErrorResponse` DTO shape
- [x] 6.3: Create `src/main/kotlin/com/decisionfabric/adapter/inbound/rest/shared/ErrorResponse.kt` — data class: `timestamp`, `status`, `error`, `message`, `correlationId`, `path`

### Part 7 — Flyway Baseline Migration
- [x] 7.1: Create `src/main/resources/db/migration/V1__baseline.sql` — empty baseline; comments describing the schema that subsequent units will add

### Part 8 — Observability Bootstrap
- [x] 8.1: Create `src/main/resources/logback-spring.xml` — JSON structured logging (Logback), MDC fields included (`correlationId`, `userId`)
- [x] 8.2: Create `src/main/kotlin/com/decisionfabric/adapter/outbound/observability/ObservabilityConfig.kt` — Micrometer `MeterRegistry` customiser (common tags: `service=decision-fabric-ai`, `env`)

### Part 9 — Unit Tests
- [x] 9.1: Create `src/test/kotlin/com/decisionfabric/adapter/inbound/security/JwtAuthenticationFilterTest.kt` — test valid JWT, expired JWT, missing token, insufficient role
- [x] 9.2: Create `src/test/kotlin/com/decisionfabric/adapter/inbound/rest/shared/CorrelationIdFilterTest.kt` — test `X-Correlation-ID` header propagation and MDC population
- [x] 9.3: Create `src/test/kotlin/com/decisionfabric/adapter/inbound/rest/shared/GlobalExceptionHandlerTest.kt` — test error response shape for domain exceptions
- [x] 9.4: Create `src/test/kotlin/com/decisionfabric/DecisionFabricAiApplicationTest.kt` — `@SpringBootTest` smoke test that context loads cleanly

### Part 10 — Documentation Summary
- [x] 10.1: Create `src/main/resources/openapi/security-scheme.yml` — OpenAPI security scheme (Bearer JWT) for later aggregation
- [x] 10.2: Create `aidlc-docs/construction/unit-1-foundation/code/unit-1-code-summary.md` — summary of generated files, key decisions, and notes for Unit 2

---

## Code Location
All source code at workspace root: `/Users/madhuy/Developer/Decision-Fabric-AI/`  
No code in `aidlc-docs/` (documentation only).

## Key Decisions Baked In
| Decision | Value |
|---|---|
| Language | Kotlin |
| Framework | Spring Boot 3.x |
| Build | Gradle (Kotlin DSL) |
| Security | Spring Security + JWT Bearer (JWKS) |
| Migrations | Flyway |
| Logging | SLF4J + Logback JSON |
| Observability | Micrometer |
| DMN engine (Unit 3) | Drools (primary) |
