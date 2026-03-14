# Units Generation Plan — Decision-Fabric-AI

## Overview
This plan decomposes Decision-Fabric-AI into units of work for the Construction phase. The application design is an established **Kotlin + Spring Boot Clean Architecture / Hexagonal** application with a single package tree (`com.decisionfabric`). Questions below focus on decisions that affect how work is sequenced and boundaries are drawn.

---

## Execution Checklist

- [x] Step 1: Analyze application-design.md and requirements.md
- [x] Step 2: Create unit decomposition plan (this document)
- [x] Step 3: Answer unit decomposition questions (Section A)
- [x] Step 4: Generate unit-of-work.md
- [x] Step 5: Generate unit-of-work-dependency.md
- [x] Step 6: Generate unit-of-work-story-map.md
- [x] Step 7: Validate unit completeness

---

## Section A: Unit Decomposition Questions

---

### Question 1
**Deployment model** — How should Decision-Fabric-AI be packaged and deployed?

A) **Single Spring Boot application** — all layers (domain, application, adapters) in one deployable JAR; logical modules within the JAR but a single process on ECS/EKS
B) **Two services** — one service for Rule Management + DMN evaluation, one service for Decision API + AI augmentation
C) **Three or more microservices** — e.g., separate services for (1) Rule Management API, (2) Decision Evaluation Engine, (3) AI Augmentation, each independently deployable and scalable

[Answer]: A

---

### Question 2
**Construction sequence** — In what order should the units of work be built during the Construction phase?

A) **Domain-first** — Domain entities → ports → application services → then adapters one by one
B) **Feature-slice-first** — one vertical slice (e.g., Rule Management) fully built end-to-end before the next slice (Decision Evaluation, then AI Augmentation)
C) **Infrastructure-first** — persistence adapters + DB schema first, then DMN engine adapter, then application services, then REST API
D) **Parallel** — multiple developers or iterations work on slices simultaneously; units are independent enough to proceed in parallel

[Answer]: B

---

### Question 3
**Database migration strategy** — How should the PostgreSQL schema be managed?

A) **Flyway** — versioned SQL migrations in `src/main/resources/db/migration/`, run automatically on Spring Boot startup
B) **Liquibase** — XML/YAML changelogs managed by Liquibase, run on startup
C) **Manual / DBA-managed** — scripts provided but not automatically applied by the application

[Answer]: A

---

### Question 4
**DMN engine unit scope** — Both `CamundaDmnEngineAdapter` and `DroolsDmnEngineAdapter` were designed. How should this be handled during Construction?

A) **Implement both** — implement Camunda and Drools adapters; `dmn.engine.provider` config selects which bean is active
B) **Implement Camunda only first** — deliver Camunda adapter as primary; Drools adapter can follow in a later iteration
C) **Implement Drools only first** — deliver Drools adapter as primary; Camunda adapter can follow in a later iteration

[Answer]: C

---

*Once all `[Answer]:` tags are filled, notify me and I will generate the three unit-of-work artifacts.*
