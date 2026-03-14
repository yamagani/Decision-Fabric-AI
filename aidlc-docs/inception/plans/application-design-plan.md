# Application Design Plan — Decision-Fabric-AI

## Overview
This plan guides the application design for Decision-Fabric-AI — a DMN-compliant, AI-enhanced rules engine built with Java/Kotlin (Spring Boot) on AWS.

Please answer all `[Answer]:` tags in Section A, then notify me to generate the design artifacts.

---

## Execution Checklist

- [x] Step 1: Analyze context (requirements.md + stories.md reviewed)
- [x] Step 2: Create application design plan (this document)
- [x] Step 3: Answer design clarification questions (Section A)
- [x] Step 4: Generate components.md
- [x] Step 5: Generate component-methods.md
- [x] Step 6: Generate services.md
- [x] Step 7: Generate component-dependency.md
- [x] Step 8: Generate application-design.md (consolidated)
- [x] Step 9: Validate design completeness

---

## Section A: Design Clarification Questions

---

### Question 1
What **architectural style** should be used for the Java/Kotlin Spring Boot application?

A) **Layered / N-Tier** — Controller → Service → Repository layers (classic Spring Boot)
B) **Clean Architecture / Hexagonal** — domain core isolated from infrastructure adapters (ports & adapters)
C) **Domain-Driven Design (DDD)** — aggregates, value objects, domain events, bounded contexts
D) **CQRS** — separate command and query models for rule management and decision evaluation
X) Other (please describe after [Answer]: tag below)

[Answer]: B

---

### Question 2
How should the **DMN engine** be integrated?

A) **Embedded library** — Camunda DMN Engine or Drools DMN as a Spring Bean (in-process, no network hop)
B) **Sidecar / external process** — DMN engine runs as a separate process and is called via local IPC or HTTP
C) **Pluggable adapter** — abstract interface with swappable implementations (Camunda or Drools selectable via config)
X) Other (please describe after [Answer]: tag below)

[Answer]: C

---

### Question 3
Where should **DMN model files** (`.dmn` XML) be stored at runtime?

A) **Relational database** (e.g., PostgreSQL) — rules table stores DMN XML as a column alongside metadata
B) **Object storage** (e.g., Amazon S3) — `.dmn` files stored in S3; metadata (ID, version, status) in a relational DB
C) **Database only** — all rule content and metadata in one relational DB table (no S3)
D) **In-memory only** — rules loaded from DB/S3 at startup and cached in memory
X) Other (please describe after [Answer]: tag below)

[Answer]: A,D

---

### Question 4
How should the **AI Augmentation** component call the external LLM API?

A) **Synchronous HTTP** — direct REST/SDK call inline within the decision evaluation request
B) **Async with timeout** — non-blocking call with a configurable timeout; falls back synchronously if exceeded
C) **Feature-flagged sync** — synchronous by default; async mode optional via config
X) Other (please describe after [Answer]: tag below)

[Answer]: A

---

### Question 5
How should **audit logs** (decision history) be persisted?

A) **Same relational DB** as rules — a separate `decision_audit` table in PostgreSQL
B) **Separate dedicated store** — e.g., Amazon DynamoDB for append-only, time-range-queryable audit records
C) **Both** — write to relational DB (short-term) and stream to a time-series / analytical store (long-term)
X) Other (please describe after [Answer]: tag below)

[Answer]: A

---

### Question 6
How should **AI provider configuration** (endpoint, model, API key, threshold) be managed?

A) **Environment variables / AWS Secrets Manager** — loaded at startup; requires restart to change
B) **Dynamic configuration API** — persisted in DB; reloaded at runtime without restart (as per US-3.3)
C) **AWS Parameter Store** — config stored in SSM Parameter Store; polled periodically (no restart needed)
X) Other (please describe after [Answer]: tag below)

[Answer]: C

---

*Once all `[Answer]:` tags are filled, notify me and I will generate all four design artifacts (components.md, component-methods.md, services.md, component-dependency.md) plus the consolidated application-design.md.*
