# User Stories — Decision-Fabric-AI

> **Organization**: Epic-Based | **Granularity**: Medium-grained | **Acceptance Criteria**: Given/When/Then (BDD)
> **Auth/RBAC**: Cross-cutting acceptance criteria embedded in every story (no standalone auth stories)
> **Error scenarios**: Included for all major failure modes

---

## Epic 1: DMN Rule Lifecycle

> Covers authoring, versioning, organizing, importing, and exporting DMN rules.
> **FR coverage**: FR-01, FR-06, FR-09

---

### US-1.1: Manage DMN Decision Rules

**As a** Rule Administrator (Alex),
**I want to** create, update, version, activate, deactivate, and delete DMN decision rules via the API,
**so that** I can maintain the current business rule logic deployed for decision evaluation without requiring code changes or deployments.

**RBAC**: Requires `rule-admin` role. Read-only access requires `rule-reader` role.

#### Acceptance Criteria

**Scenario 1: Create a new DMN decision rule**
```gherkin
Given I am authenticated with a valid JWT carrying the "rule-admin" role
When I POST /rules with a valid rule payload (name, description, FEEL-based conditions, actions, priority)
Then the system creates the rule with status "INACTIVE"
And returns HTTP 201 with the rule ID, version "1", and created timestamp
And the rule is persisted and queryable via GET /rules/{id}
```

**Scenario 2: Update an existing rule (creates new version)**
```gherkin
Given an existing rule with ID "rule-001" at version 1
And I am authenticated with the "rule-admin" role
When I PUT /rules/rule-001 with updated conditions
Then the system saves a new version "2" of the rule
And the previous version "1" is retained and queryable
And the response includes the new version number and updated timestamp
```

**Scenario 3: Activate a rule**
```gherkin
Given a rule with ID "rule-001" at version 2 with status "INACTIVE"
And I am authenticated with the "rule-admin" role
When I POST /rules/rule-001/activate
Then the rule status changes to "ACTIVE"
And it is included in subsequent decision evaluations
And the activation event is written to the audit log
```

**Scenario 4: Deactivate a rule**
```gherkin
Given a rule with ID "rule-001" with status "ACTIVE"
And I am authenticated with the "rule-admin" role
When I POST /rules/rule-001/deactivate
Then the rule status changes to "INACTIVE"
And it is excluded from subsequent decision evaluations immediately
And the deactivation event is written to the audit log
```

**Scenario 5: Delete a rule**
```gherkin
Given a rule with ID "rule-001" that is "INACTIVE"
And I am authenticated with the "rule-admin" role
When I DELETE /rules/rule-001
Then the rule is marked deleted and no longer returned in active rule queries
And returns HTTP 204
```

**Scenario 6: Attempt to delete an active rule**
```gherkin
Given a rule with ID "rule-001" that is "ACTIVE"
And I am authenticated with the "rule-admin" role
When I DELETE /rules/rule-001
Then the system returns HTTP 409 Conflict
And the response includes an error message: "Rule must be deactivated before deletion"
And the rule remains active and undeleted
```

**Scenario 7: Unauthorized access attempt**
```gherkin
Given a request with a JWT carrying only the "rule-reader" role
When I POST /rules with a new rule payload
Then the system returns HTTP 403 Forbidden
And the rule is not created
```

**Scenario 8: Unauthenticated access attempt**
```gherkin
Given a request with no Authorization header
When I call any /rules endpoint
Then the system returns HTTP 401 Unauthorized
```

---

### US-1.2: Import and Export DMN Models

**As a** Rule Administrator (Alex),
**I want to** import a standard `.dmn` XML file to create rules and export existing rules as `.dmn` XML,
**so that** I can migrate business logic from DMN-authoring tools (e.g., Camunda Modeler) and maintain vendor portability.

**RBAC**: Import requires `rule-admin` role. Export requires `rule-admin` or `rule-reader` role.

#### Acceptance Criteria

**Scenario 1: Successful DMN model import**
```gherkin
Given I am authenticated with the "rule-admin" role
And I have a valid DMN 1.4-compliant .dmn XML file
When I POST /rules/import with the .dmn file as multipart/form-data
Then the system validates the file against the DMN 1.4 schema
And creates the corresponding rules in "INACTIVE" status
And returns HTTP 201 with a list of created rule IDs and names
```

**Scenario 2: Import a DMN model with invalid schema**
```gherkin
Given I am authenticated with the "rule-admin" role
And I have a .dmn file that does not conform to DMN 1.4 schema
When I POST /rules/import with the invalid file
Then the system returns HTTP 422 Unprocessable Entity
And the response includes a validation error listing the schema violations
And no rules are created (atomic import — all or nothing)
```

**Scenario 3: Export rules as DMN XML**
```gherkin
Given one or more rules exist in the system
And I am authenticated with the "rule-admin" or "rule-reader" role
When I GET /rules/export?ids=rule-001,rule-002
Then the system returns a valid DMN 1.4-compliant .dmn XML file
And the file contains all requested rules as decision elements
And the response Content-Type is "application/xml"
```

**Scenario 4: Import a DMN file with unsupported DMN version**
```gherkin
Given I am authenticated with the "rule-admin" role
And I have a .dmn file using DMN 1.1 namespace
When I POST /rules/import with the file
Then the system returns HTTP 422
And the error message specifies "Unsupported DMN version. Only DMN 1.4 is supported."
```

---

### US-1.3: Manage Rule Sets

**As a** Rule Administrator (Alex),
**I want to** group rules into named rule sets, and enable, disable, or reorder them independently,
**so that** I can logically organize rules by business domain and control which groups are active without touching individual rules.

**RBAC**: Write operations require `rule-admin` role. Read requires `rule-reader` role.

#### Acceptance Criteria

**Scenario 1: Create a rule set and add rules**
```gherkin
Given I am authenticated with the "rule-admin" role
When I POST /rule-sets with a name, description, and list of rule IDs
Then the system creates the rule set with status "ENABLED"
And returns HTTP 201 with the rule set ID and member rule IDs
```

**Scenario 2: Disable a rule set**
```gherkin
Given a rule set "fraud-checks" with status "ENABLED"
And I am authenticated with the "rule-admin" role
When I POST /rule-sets/fraud-checks/disable
Then all rules in the set are excluded from decision evaluation
And the rule set status changes to "DISABLED"
And the change is logged in the audit trail
```

**Scenario 3: Reorder rules within a rule set**
```gherkin
Given a rule set with 3 rules evaluated in priority order
And I am authenticated with the "rule-admin" role
When I PUT /rule-sets/{id}/order with an ordered list of rule IDs
Then the system updates the evaluation priority order
And subsequent evaluations use the new order
```

**Scenario 4: Add a non-existent rule to a rule set**
```gherkin
Given I am authenticated with the "rule-admin" role
When I POST /rule-sets with a rule ID that does not exist
Then the system returns HTTP 404
And the error message specifies which rule ID was not found
And no rule set is created
```

---

## Epic 2: Decision Evaluation

> Covers submitting decision requests and receiving structured outcomes.
> **FR coverage**: FR-02, FR-03

---

### US-2.1: Evaluate a Business Decision

**As a** Decision Consumer (Jordan),
**I want to** submit a structured JSON decision request to the API and receive a deterministic response,
**so that** my application can delegate business decisions to the rules engine and use the outcome programmatically.

**RBAC**: Requires `decision-consumer` role (or higher).

#### Acceptance Criteria

**Scenario 1: Successful decision evaluation — rule match**
```gherkin
Given one or more ACTIVE rules exist in the system
And I am authenticated with a valid JWT carrying the "decision-consumer" role
When I POST /decisions/evaluate with a valid JSON input payload
Then the system evaluates the input against all active rules
And returns HTTP 200 with:
  - "outcome": the decision result
  - "matchedRules": list of rule IDs and names that matched
  - "confidenceScore": a decimal between 0.0 and 1.0
  - "aiAugmented": false
  - "explanation": human-readable description of matched rule(s)
  - "requestId": a unique UUID
  - "evaluatedAt": ISO 8601 timestamp
And the evaluation completes within 200ms (p99)
```

**Scenario 2: Decision evaluation — no rule matches**
```gherkin
Given the input payload does not match any active rule
When I POST /decisions/evaluate with the payload
Then the system returns HTTP 200 with:
  - "outcome": "NO_MATCH"
  - "matchedRules": []
  - "confidenceScore": 0.0
  - "aiAugmented": false
  - "explanation": "No active rules matched the provided input"
```

**Scenario 3: Invalid JSON input payload**
```gherkin
Given I POST /decisions/evaluate with a malformed or missing required fields payload
Then the system returns HTTP 400 Bad Request
And the response lists the specific validation errors per field
And no decision is evaluated or logged
```

**Scenario 4: Decision evaluation with no active rules**
```gherkin
Given no rules are in ACTIVE status
When I POST /decisions/evaluate
Then the system returns HTTP 200 with outcome "NO_MATCH" and an explanation of no active rules
```

---

### US-2.2: Handle Invalid Decision Request

**As a** Decision Consumer (Jordan),
**I want to** receive clear, structured error responses when my decision request is malformed or unauthorized,
**so that** I can diagnose and fix integration issues quickly without guessing the cause.

**RBAC**: Applies to all callers regardless of role.

#### Acceptance Criteria

**Scenario 1: Missing required input fields**
```gherkin
Given I POST /decisions/evaluate with a payload missing a required field (e.g., "inputData")
Then the system returns HTTP 400 Bad Request
And the response body includes a machine-readable error code and a human-readable message
And the message identifies the missing field(s) by name
```

**Scenario 2: Request payload exceeds size limit**
```gherkin
Given I POST /decisions/evaluate with a payload larger than the configured max size
Then the system returns HTTP 413 Payload Too Large
And the response includes the maximum allowed size
```

**Scenario 3: Unauthorized caller — wrong role**
```gherkin
Given I am authenticated with a JWT carrying only the "rule-admin" role
When I POST /decisions/evaluate
Then the system returns HTTP 403 Forbidden
And the response body specifies the required role
```

**Scenario 4: Rate limit exceeded**
```gherkin
Given I have exceeded the configured API rate limit for my API key
When I POST /decisions/evaluate
Then the system returns HTTP 429 Too Many Requests
And the response includes a Retry-After header
```

---

## Epic 3: AI Augmentation Management

> Covers AI-augmented decision responses, fallback behaviour, and configuration.
> **FR coverage**: FR-02, FR-04

---

### US-3.1: Receive AI-Augmented Decision Response

**As a** Decision Consumer (Jordan),
**I want to** know in the decision response when AI augmentation was used to resolve my request, including the AI's reasoning,
**so that** my application and end users can distinguish AI-resolved decisions from pure rule-matched decisions and apply appropriate confidence handling.

**RBAC**: Requires `decision-consumer` role.

#### Acceptance Criteria

**Scenario 1: Decision resolved by AI augmentation**
```gherkin
Given a decision request whose input matches no rules above the confidence threshold
And the AI augmentation provider is available
When I POST /decisions/evaluate
Then the system calls the configured external LLM API
And returns HTTP 200 with:
  - "aiAugmented": true
  - "aiReasoning": a non-empty string summarizing the LLM's reasoning
  - "outcome": the AI-resolved decision outcome
  - "confidenceScore": the AI-assigned confidence score
  - "matchedRules": any partially matched rules (may be empty)
And the response identifies the LLM model used (e.g., "modelUsed": "amazon.titan-text-express-v1")
And the total response time is within 2 seconds (p99)
```

**Scenario 2: AI augmentation not triggered — rule confidence above threshold**
```gherkin
Given a decision request that matches a rule with confidence above the configured threshold
When I POST /decisions/evaluate
Then the system returns the rule-matched outcome
And "aiAugmented" is false in the response
And the LLM API is NOT called
```

**Scenario 3: AI reasoning included in audit log**
```gherkin
Given an AI-augmented decision was evaluated
When I query GET /decisions/{requestId}
Then the audit record includes the "aiReasoning" field and "aiAugmented": true
```

---

### US-3.2: Handle AI Provider Unavailability

**As a** Decision Consumer (Jordan) and System Integrator (Sam),
**I want to** receive a valid decision response even when the external LLM API is unavailable,
**so that** my application does not fail during AI provider outages and the system continues evaluating decisions using rules only.

**RBAC**: Applies to all decision-consumer callers.

#### Acceptance Criteria

**Scenario 1: LLM API unavailable — fallback to rule-only evaluation**
```gherkin
Given the configured LLM API endpoint is unreachable or returns an error
And a circuit breaker is configured for the LLM provider
When I POST /decisions/evaluate with an input that would normally trigger AI augmentation
Then the system falls back to rule-only evaluation
And returns HTTP 200 with:
  - "aiAugmented": false
  - "fallbackReason": "AI provider unavailable — rule-only evaluation applied"
  - "outcome": the best rule-only outcome (or "NO_MATCH" if no rules match)
And the fallback is recorded in the audit log
```

**Scenario 2: LLM API returns timeout**
```gherkin
Given the LLM API call exceeds the configured timeout
When I POST /decisions/evaluate
Then the system applies the same fallback as Scenario 1
And the circuit breaker records a failure event
```

**Scenario 3: Circuit breaker open — LLM calls skipped**
```gherkin
Given the circuit breaker for the LLM is in OPEN state (threshold of failures reached)
When I POST /decisions/evaluate
Then the system skips the LLM call entirely
And applies rule-only evaluation immediately
And the response includes "circuitBreakerState": "OPEN"
```

---

### US-3.3: Configure AI Augmentation Settings

**As a** System Integrator (Sam),
**I want to** configure the LLM provider endpoint, model, API credentials, and confidence threshold via the API without redeploying the service,
**so that** I can update AI provider settings operationally and tune when AI augmentation is triggered.

**RBAC**: Requires `system-admin` role.

#### Acceptance Criteria

**Scenario 1: Update the LLM provider configuration**
```gherkin
Given I am authenticated with the "system-admin" role
When I PUT /config/ai-provider with:
  - "providerEndpoint": "https://bedrock.us-east-1.amazonaws.com"
  - "modelId": "amazon.titan-text-express-v1"
  - "confidenceThreshold": 0.75
  - "timeoutMs": 3000
Then the system validates and persists the configuration
And returns HTTP 200 with the updated configuration
And subsequent decision evaluations use the new settings immediately
```

**Scenario 2: Update API key / credentials**
```gherkin
Given I am authenticated with the "system-admin" role
When I PUT /config/ai-provider/credentials with new API key or IAM role ARN
Then the system updates the stored credentials securely
And does not return the credential values in the response (returns masked or omitted)
And returns HTTP 200 with a confirmation message
```

**Scenario 3: Invalid confidence threshold value**
```gherkin
Given I PUT /config/ai-provider with "confidenceThreshold": 1.5 (out of range 0.0–1.0)
Then the system returns HTTP 400 Bad Request
And the error message specifies the valid range for the field
And the existing configuration remains unchanged
```

**Scenario 4: Unauthorized configuration attempt**
```gherkin
Given I am authenticated with the "rule-admin" role (not system-admin)
When I PUT /config/ai-provider
Then the system returns HTTP 403 Forbidden
```

---

## Epic 4: Audit & Decision History

> Covers querying and reviewing past decision evaluations.
> **FR coverage**: FR-05

---

### US-4.1: View Decision History

**As a** Business Analyst (Taylor) and Decision Consumer (Jordan),
**I want to** query past decision evaluations by request ID or time range,
**so that** I can audit decision outcomes, investigate specific cases for compliance, and verify the system is behaving as expected.

**RBAC**: Requires `audit-reader` role (or `rule-admin`, `system-admin` roles which supersede).

#### Acceptance Criteria

**Scenario 1: Retrieve a specific decision by request ID**
```gherkin
Given a past decision evaluation with requestId "req-abc-123"
And I am authenticated with the "audit-reader" role
When I GET /decisions/req-abc-123
Then the system returns HTTP 200 with the full audit record including:
  - requestId, evaluatedAt (ISO 8601)
  - inputPayload (the original JSON input)
  - matchedRules (rule IDs, names, version numbers)
  - outcome
  - confidenceScore
  - aiAugmented flag
  - aiReasoning (if applicable)
  - ruleSetId (if applicable)
```

**Scenario 2: Query decision history by time range**
```gherkin
Given multiple decision evaluations have been recorded
And I am authenticated with the "audit-reader" role
When I GET /decisions?from=2026-03-01T00:00:00Z&to=2026-03-13T23:59:59Z&limit=50
Then the system returns a paginated list of decision records within the time range
And results are sorted by evaluatedAt descending
And the response includes a "nextPageToken" if more results exist
```

**Scenario 3: Query with filter for AI-augmented decisions only**
```gherkin
Given I am authenticated with the "audit-reader" role
When I GET /decisions?aiAugmented=true&from=2026-03-01T00:00:00Z
Then only AI-augmented decisions are returned in the results
```

**Scenario 4: Request ID not found**
```gherkin
Given no decision exists with ID "req-unknown-999"
When I GET /decisions/req-unknown-999
Then the system returns HTTP 404 Not Found
And the response includes a message: "Decision record not found"
```

**Scenario 5: Decision records older than 90-day retention window**
```gherkin
Given a decision record with evaluatedAt more than 90 days ago
When I GET /decisions/{requestId} for that record
Then the system returns HTTP 404 or HTTP 410 Gone
And the response message references the retention policy
```

---

## Epic 5: System Operations & Observability

> Covers health monitoring, metrics, and operational visibility.
> **FR coverage**: FR-07

---

### US-5.1: Monitor System Health

**As a** System Integrator / DevOps Engineer (Sam),
**I want to** check the liveness and readiness state of the service and retrieve operational metrics,
**so that** I can integrate Decision-Fabric-AI into load balancers, auto-scalers, and monitoring dashboards and detect degradation before it impacts consumers.

**RBAC**: Health endpoints are unauthenticated (for load balancer probes). Metrics endpoint requires `system-admin` role.

#### Acceptance Criteria

**Scenario 1: Liveness probe returns healthy**
```gherkin
Given the application process is running
When I GET /actuator/health/liveness
Then the system returns HTTP 200 with body: {"status": "UP"}
And the response time is under 50ms
```

**Scenario 2: Readiness probe reflects downstream dependency state**
```gherkin
Given the application is running but the database connection is unavailable
When I GET /actuator/health/readiness
Then the system returns HTTP 503 Service Unavailable
And the response includes: {"status": "DOWN", "components": {"db": {"status": "DOWN"}}}
And the load balancer stops routing traffic to this instance
```

**Scenario 3: Retrieve operational metrics**
```gherkin
Given I am authenticated with the "system-admin" role
When I GET /actuator/metrics
Then the system returns current values for:
  - decision.requests.total (counter)
  - decision.latency.p99 (histogram)
  - ai.invocations.total (counter)
  - ai.fallback.total (counter)
  - decision.errors.total (counter)
  - circuit.breaker.state (gauge: 0=CLOSED, 1=OPEN, 2=HALF_OPEN)
```

**Scenario 4: Structured log includes correlation ID**
```gherkin
Given a decision evaluation request is received with a "X-Correlation-ID" header
When the system processes and responds
Then all log entries emitted during that request include the correlationId field
And no sensitive fields (API keys, PII, JWT tokens) appear in any log entry
```

**Scenario 5: Health endpoint accessible without authentication**
```gherkin
Given no Authorization header is provided
When I GET /actuator/health/liveness
Then the system returns HTTP 200 (health probes are public)
When I GET /actuator/metrics
Then the system returns HTTP 401 (metrics require authentication)
```

---

## Story Coverage Summary

| Story | FR-01 | FR-02 | FR-03 | FR-04 | FR-05 | FR-06 | FR-07 | FR-08 | FR-09 |
|---|---|---|---|---|---|---|---|---|---|
| US-1.1: Manage DMN Decision Rules | ✅ | | | | | ✅ | | ✅ (RBAC) | ✅ (FEEL) |
| US-1.2: Import and Export DMN Models | ✅ | | | | | | | ✅ (RBAC) | ✅ (DMN XML) |
| US-1.3: Manage Rule Sets | ✅ | | | | | ✅ | | ✅ (RBAC) | |
| US-2.1: Evaluate a Business Decision | | ✅ | ✅ | | | | | ✅ (RBAC) | |
| US-2.2: Handle Invalid Decision Request | | | ✅ | | | | | ✅ (RBAC) | |
| US-3.1: Receive AI-Augmented Decision | | ✅ | | | | | | ✅ (RBAC) | |
| US-3.2: Handle AI Provider Unavailability | | ✅ | | ✅ | | | | | |
| US-3.3: Configure AI Augmentation Settings | | | | ✅ | | | | ✅ (RBAC) | |
| US-4.1: View Decision History | | | | | ✅ | | | ✅ (RBAC) | |
| US-5.1: Monitor System Health | | | | | | | ✅ | ✅ (RBAC) | |

**All 9 functional requirements (FR-01 through FR-09) are covered across the 10 stories.**
