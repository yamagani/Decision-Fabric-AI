# Personas — Decision-Fabric-AI

> **4 personas** modelled for Decision-Fabric-AI covering the full spectrum of users who interact with the system.

---

## Persona 1: Alex — Rule Administrator

| Field | Detail |
|---|---|
| **Name** | Alex |
| **Role** | Rule Administrator |
| **Technical Proficiency** | Medium-High — comfortable with decision tables, basic FEEL expressions, REST APIs. Not a software engineer but technically literate. |
| **Organization Context** | Business operations or compliance team in an enterprise |

### Goals
- Author, version, and deploy DMN decision rules without breaking live decision evaluations
- Organize rules into logical rule sets, enable/disable them independently
- Import existing business logic from `.dmn` files and keep rules up-to-date
- Understand which rule version is active in production at any given time

### Pain Points
- Deploying a new rule version without knowing its impact on live traffic
- Difficulty tracing which version of a rule produced a specific past decision
- Validating complex FEEL expressions before deployment
- Coordinating rule changes with consuming teams

### Relevant Stories
- US-1.1: Manage DMN Decision Rules
- US-1.2: Import and Export DMN Models
- US-1.3: Manage Rule Sets

---

## Persona 2: Jordan — Decision Consumer

| Field | Detail |
|---|---|
| **Name** | Jordan |
| **Role** | Decision Consumer (API Caller) |
| **Technical Proficiency** | High — software engineer building an application that calls the Decision-Fabric-AI evaluation API |
| **Organization Context** | Product or platform engineering team |

### Goals
- Receive fast, deterministic decision responses for structured business logic
- Understand exactly which rule matched and why a decision was made
- Know when AI augmentation was used and why
- Integrate the API reliably with confidence in SLA and fallback behaviour

### Pain Points
- Variable latency when AI augmentation is triggered
- Opaque decision responses that don't explain the outcome
- Unexpected API failures not clearly communicated in error responses
- Debugging failed decisions without sufficient context in the response

### Relevant Stories
- US-2.1: Evaluate a Business Decision
- US-2.2: Handle Invalid Decision Request
- US-3.1: Receive AI-Augmented Decision Response
- US-3.2: Handle AI Provider Unavailability

---

## Persona 3: Sam — System Integrator / DevOps Engineer

| Field | Detail |
|---|---|
| **Name** | Sam |
| **Role** | System Integrator / DevOps Engineer |
| **Technical Proficiency** | High — deploys and operates the platform; configures AWS infrastructure, LLM providers, monitoring |
| **Organization Context** | Platform engineering, SRE, or DevOps team |

### Goals
- Configure LLM provider details (endpoint, model, API key, threshold) without code changes
- Monitor system health, decision throughput, error rates, and AI invocation rates
- Ensure the system degrades gracefully when external dependencies (LLM APIs) are unavailable
- Have clear observability into the system via structured logs and metrics

### Pain Points
- LLM provider outages causing cascading failures
- Insufficient metrics to detect performance degradation early
- Rotating API credentials requiring service restarts
- Unclear separation between infrastructure configuration and application logic

### Relevant Stories
- US-3.3: Configure AI Augmentation Settings
- US-5.1: Monitor System Health
- US-3.2: Handle AI Provider Unavailability

---

## Persona 4: Taylor — Business Analyst

| Field | Detail |
|---|---|
| **Name** | Taylor |
| **Role** | Business Analyst (Read-Only Viewer) |
| **Technical Proficiency** | Low-Medium — understands business rules conceptually; not comfortable making API calls directly; uses internal tooling or reports |
| **Organization Context** | Business analysis, compliance, or audit function |

### Goals
- Review which business rules are currently active and understand their logic
- Search and review past decision outcomes for compliance and audit purposes
- Verify that deployed rules match approved business policy
- Produce or review audit reports spanning a time range

### Pain Points
- Rule configurations stored in technical formats (FEEL, XML) that are hard to read
- No consolidated view of decision history without writing API queries
- Difficulty correlating a decision outcome back to the specific rule version that produced it
- Lack of narrative explanation in decision logs

### Relevant Stories
- US-4.1: View Decision History
- US-1.1: Manage DMN Decision Rules (read-only view)

---

## Persona-to-Story Mapping

| Story | Alex (Rule Admin) | Jordan (Decision Consumer) | Sam (Integrator/DevOps) | Taylor (Business Analyst) |
|---|---|---|---|---|
| US-1.1: Manage DMN Decision Rules | Primary | — | — | Read-only |
| US-1.2: Import and Export DMN Models | Primary | — | — | — |
| US-1.3: Manage Rule Sets | Primary | — | — | — |
| US-2.1: Evaluate a Business Decision | — | Primary | — | — |
| US-2.2: Handle Invalid Decision Request | — | Primary | — | — |
| US-3.1: Receive AI-Augmented Decision Response | — | Primary | — | — |
| US-3.2: Handle AI Provider Unavailability | — | Primary | Primary | — |
| US-3.3: Configure AI Augmentation Settings | — | — | Primary | — |
| US-4.1: View Decision History | — | Secondary | — | Primary |
| US-5.1: Monitor System Health | — | — | Primary | — |
