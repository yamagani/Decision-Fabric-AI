# Unit of Work Story Map — Decision-Fabric-AI

## Mapping Overview

All 10 user stories and 5 epics are fully assigned to units. FR-07 (RBAC) is a cross-cutting concern implemented in Unit 1 and enforced across all units.

---

## Story → Unit Assignment

| Story | Title | Unit | Primary FR |
|---|---|---|---|
| US-1.1 | Create and Update DMN Rule | **Unit 2** | FR-01 |
| US-1.2 | Activate DMN Rule Version | **Unit 2** | FR-02 |
| US-1.3 | Import/Export DMN File | **Unit 2** | FR-09 |
| US-2.1 | Evaluate Decision (Rule-Only) | **Unit 3** | FR-03 |
| US-2.2 | Evaluate Decision (AI-Augmented) | **Unit 3** (rule path) + **Unit 4** (AI path) | FR-03, FR-04 |
| US-3.1 | Configure LLM Provider | **Unit 4** | FR-08 |
| US-3.2 | AI Augmentation with Fallback | **Unit 4** | FR-04 |
| US-3.3 | Update AI Config Without Restart | **Unit 4** | FR-08 |
| US-4.1 | Query Decision History | **Unit 5** | FR-06 |
| US-5.1 | System Health & Observability | **Unit 5** | NFR-07 |

---

## Epic → Unit Mapping

| Epic | Title | Unit(s) |
|---|---|---|
| Epic 1: DMN Rule Lifecycle | Manage DMN rules, versions, import/export | Unit 2 |
| Epic 2: Decision Evaluation | Evaluate decisions with DMN rules | Unit 3 |
| Epic 3: AI Augmentation | AI-enhanced decisions and dynamic config | Unit 4 |
| Epic 4: Audit & History | Decision history queries | Unit 5 |
| Epic 5: System Operations | Health checks, metrics, observability | Unit 5 |

---

## Requirement Coverage by Unit

| Unit | Functional Requirements | Non-Functional Requirements |
|---|---|---|
| Unit 1: Foundation | — (enables all FRs via RBAC, FR-07) | NFR-03 (Security — JWT RBAC), NFR-04 (Scalability — stateless config), NFR-06 (Maintainability — hexagonal base) |
| Unit 2: Rule Management | FR-01, FR-02, FR-09 | NFR-01 (Performance — cache), NFR-06 (Maintainability) |
| Unit 3: Decision Evaluation | FR-03, FR-05 | NFR-01 (p99 < 200ms rule-only), NFR-02 (Reliability), NFR-05 (Auditability) |
| Unit 4: AI Augmentation | FR-04, FR-08 | NFR-01 (p99 < 2s AI path), NFR-02 (circuit breaker), NFR-03 (Secrets Manager) |
| Unit 5: Audit & Operations | FR-06 | NFR-05 (Auditability), NFR-07 (Operability) |

---

## Unit Detail: Story Acceptance Criteria Mapping

### Unit 2 — Rule Management

**US-1.1: Create and Update DMN Rule**
- Given: Authenticated `rule-admin`
- Actions: `POST /api/v1/rules`, `PUT /api/v1/rules/{id}`
- Acceptance: Rule persisted to PostgreSQL; version auto-incremented; 422 on invalid DMN; 403 on insufficient role

**US-1.2: Activate DMN Rule Version**
- Given: Authenticated `rule-admin`; existing rule with multiple versions
- Actions: `POST /api/v1/rules/{id}/versions/{version}/activate`
- Acceptance: Previous active version deactivated; new version set to ACTIVE; `InMemoryRuleCache` reloaded via `RuleLifecycleEvent`; audit entry created

**US-1.3: Import/Export DMN File**
- Given: Authenticated `rule-admin` (import) or `rule-reader` (export)
- Actions: `POST /api/v1/rules/import` (multipart `.dmn`), `GET /api/v1/rules/{id}/export`
- Acceptance: Import validates against OMG DMN 1.4 schema; extracted decisions become rules; export returns valid `.dmn` XML; 422 on schema violation

---

### Unit 3 — Decision Evaluation

**US-2.1: Evaluate Decision (Rule-Only)**
- Given: Authenticated `decision-consumer`; active rules in cache
- Action: `POST /api/v1/decisions/evaluate` with `aiAugmented: false` or confidence above threshold
- Acceptance: `DecisionResult` returned with matched rules, outputs, p99 < 200ms; decision appended to `decision_audit`

**US-2.2: Evaluate Decision (Full Path — rule path)**
- Given: Authenticated `decision-consumer`
- Action: `POST /api/v1/decisions/evaluate`
- Acceptance (Unit 3 scope): DMN evaluation complete; result ready for AI augmentation (Unit 4 wires in the AI call); fallback returns rule-only result if AI unavailable

---

### Unit 4 — AI Augmentation & Config

**US-2.2: Evaluate Decision (AI path)**
- Acceptance: When `confidenceScore < threshold`, `LlmProviderPort.augmentDecision()` called; result includes `aiAugmented: true`, `reasoning`, updated confidence

**US-3.1: Configure LLM Provider**
- Given: Authenticated `system-admin`
- Action: `PUT /api/v1/config/ai` with provider, model, threshold
- Acceptance: Config stored in AWS SSM; `AiProviderConfigChangedEvent` published; `DecisionEvaluationService` uses new threshold immediately

**US-3.2: AI Augmentation with Fallback**
- Acceptance: When LLM API fails or circuit breaker is OPEN, `DecisionEvaluationService` returns rule-only result with `aiAugmented: false`, no 5xx to client

**US-3.3: Update AI Config Without Restart**
- Acceptance: SSM parameter changed externally; `AwsParameterStoreConfigAdapter` polls and detects change within configured interval; no application restart required

---

### Unit 5 — Audit Query & Operations

**US-4.1: Query Decision History**
- Given: Authenticated `audit-reader`
- Action: `GET /api/v1/decisions/history?ruleId=&userId=&from=&to=&page=&size=`
- Acceptance: Paginated results; filterable by rule, user, date range; 403 for non-audit-reader roles; 30-day retention policy honoured

**US-5.1: System Health & Observability**
- Given: Ops team / monitoring agent
- Action: `GET /actuator/health`, `GET /actuator/metrics`
- Acceptance: DB connectivity check; DMN engine readiness check; SSM connectivity check; Micrometer metrics exported to CloudWatch; JSON structured logs with `correlationId`

---

## Coverage Verification

| Requirement | Covered In | Status |
|---|---|---|
| FR-01 Rule CRUD | Unit 2 (US-1.1) | ✅ |
| FR-02 Rule Versioning | Unit 2 (US-1.2) | ✅ |
| FR-03 Decision Evaluation | Unit 3 (US-2.1, US-2.2) | ✅ |
| FR-04 AI Augmentation | Unit 4 (US-2.2, US-3.2) | ✅ |
| FR-05 Audit Trail | Unit 3 (decision_audit persistence) | ✅ |
| FR-06 History Queries | Unit 5 (US-4.1) | ✅ |
| FR-07 RBAC | Unit 1 (Spring Security, cross-cutting) | ✅ |
| FR-08 AI Config Management | Unit 4 (US-3.1, US-3.3) | ✅ |
| FR-09 DMN Compliance | Unit 2 (import/export/validation), Unit 3 (Drools DMN engine) | ✅ |
| NFR-01 Performance | Unit 2 (cache), Unit 3 (p99 < 200ms), Unit 4 (p99 < 2s with circuit breaker) | ✅ |
| NFR-02 Reliability | Unit 4 (Resilience4j), Unit 1 (multi-AZ config) | ✅ |
| NFR-03 Security | Unit 1 (JWT RBAC, TLS), Unit 4 (Secrets Manager) | ✅ |
| NFR-04 Scalability | Unit 1 (stateless design), Unit 2 (cache per instance) | ✅ |
| NFR-05 Auditability | Unit 3 (append), Unit 5 (query) | ✅ |
| NFR-06 Maintainability | Unit 1 (hexagonal architecture base) | ✅ |
| NFR-07 Operability | Unit 5 (Actuator, Micrometer, structured logs) | ✅ |
