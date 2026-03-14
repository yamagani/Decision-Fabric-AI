DecisionFabric AI: AI‑driven DMN‑compliant business rules engine with GraphQL support

1. Project overview

DecisionFabric AI is a proposed platform that combines a Decision Model and Notation (DMN)‑compliant business rules engine with an AI‑driven development life cycle (AI‑DLC) and GraphQL/REST APIs.  The objective is to build a rules engine that lets business analysts design decision logic using DMN, leverages large language models (LLMs) to accelerate development of decision artifacts and APIs, and exposes the resulting rules and metadata through a modern GraphQL interface.  DMN provides a precise language for decision modelling that is easily readable by business people and analysts and reduces the effort and risk of automation projects ￼.  GraphQL allows clients to request exactly the data they need for decision services ￼, and the AI‑DLC organizes the development process into structured phases with human approval gates.

2. AI‑DLC process adapted to a DMN rules engine

2.1 Phases and milestones
	1.	Discovery (for brownfield projects) – Analyze any existing rules or codebases to identify current decision logic, data structures and DMN artifacts.  Reverse‑engineer decision requirement diagrams (DRDs) and decision tables where possible.  The AI module must produce a summary and highlight gaps that require human input.
	2.	Inception – From a business problem statement, the AI module generates an initial Decision Requirements Diagram (DRD) that outlines input data, decisions and knowledge sources.  It drafts decision tables for simple policies and collects clarifying questions for incomplete logic.  Business stakeholders review the DRD and decision tables for correctness and completeness before approving.
	3.	Construction – Using approved DRDs and decision tables, the AI module:
	•	Converts draft decision tables into DMN XML files and FEEL expressions.
	•	Scaffolds a GraphQL schema that reflects the DMN model (e.g., types for inputs and outputs, queries to evaluate decisions).  The AI asks whether GraphQL or REST is preferred; for GraphQL, it generates type definitions and resolvers; for REST, it generates OpenAPI definitions and controllers.
	•	Generates the runtime code that loads DMN models, evaluates decisions and maps them to API responses.  The code should integrate an open‑source DMN engine (e.g., Drools, Camunda) or a custom interpreter.  It must also include unit tests for each decision table.
	•	Creates human‑readable documentation (Markdown or HTML) explaining the decisions, the DRD and the API endpoints.
	4.	Integration – The AI module creates CI/CD pipelines and deployment scripts, sets up versioning and access control, and publishes the DMN models and GraphQL API.  Human approval is required before deploying to production.
	5.	Iteration – After initial release, new requirements follow the same loop: update DRDs, generate new decision tables, update APIs, test, and deploy after approval.

2.2 Human oversight and state management
	•	Approval gates – At the end of each phase (DRD creation, DMN modeling, API scaffolding and deployment) the system must stop and require explicit human approval before proceeding.
	•	Persistent state – Maintain a session store with project name, current phase, DMN artifacts, generated code and approvals to enable pause/resume and traceability.

3. Functional requirements

3.1 DMN compliance
	•	Decision modeling – Support the core elements of the DMN standard: Decision Requirement Diagrams (DRDs), decision tables and FEEL expressions.  DMN provides a way to model decision logic in a format easily understood by business people and technical users ￼.
	•	Decision tables – Allow multi‑input/multi‑output tables with hit policies (UNIQUE, FIRST, COLLECT) and row annotations.  Provide a spreadsheet‑style editor in the UI and export tables as DMN XML.
	•	DRDs and dependencies – Represent relationships between decisions, input data and knowledge sources.  Ensure that the system enforces acyclic dependencies and validates that all required inputs are mapped.
	•	FEEL support – Implement a restricted subset of Friendly Enough Expression Language (FEEL) to express conditions and calculations in decision tables.  Include simple comparisons, ranges, list membership and logical expressions.
	•	Model import/export – Support import of external DMN 1.x models and export of generated models to ensure portability and compliance with OMG’s DMN specification.

3.2 AI‑assisted content generation
	•	LLM prompt interface – Provide a secure interface to an LLM for generating DRDs, decision tables, FEEL expressions and GraphQL schema based on user prompts.  The system must log prompts and responses for audit.  Sensitive data must be redacted.
	•	Automated scaffolding – The LLM must generate boilerplate code for evaluating DMN models using a selected DMN engine and exposing endpoints through GraphQL or REST.  Include error handling and unit tests.
	•	Model refinement – The LLM must ask clarifying questions when the business problem lacks sufficient detail and incorporate user feedback into updated models.

3.3 GraphQL/REST API support
	•	GraphQL schema – Generate a GraphQL schema that mirrors the DMN models.  Provide queries such as:
	•	decisionList – list available decisions with metadata (name, description, version).
	•	decision(id: ID!, input: JSON) – evaluate a decision given an input object and return the result and execution trace.
	•	dmnModel(id: ID!) – retrieve the DMN XML for a specific decision or DRD.
	•	REST API – Provide parallel REST endpoints for clients that prefer REST.  Use HTTP verbs (GET /decisions, POST /decisions/{id}/run).  Return JSON responses with outputs and traces.
	•	Authentication and authorization – Enforce OAuth or API‑key authentication on all endpoints.  Implement role‑based access (modeler, reviewer, executor) to restrict who can publish or run decisions.
	•	Input/output format – Accept JSON objects that map to input data defined in the DMN models and return JSON objects for results.  Provide input schema validation and clear error messages.
	•	JSON interoperability – The DMN specification defines decision models in XML; there is no official DMN‑in‑JSON syntax.  When DMN models are published as executable decision services, the service inputs must be provided in XML or JSON and the outputs are returned in the same formats ￼.  Modern DMN tools automatically map XML and JSON field names to the FEEL variables defined in the model ￼ and allow clients to call decision services using JSON payloads ￼.  The engine must therefore map incoming JSON to FEEL variables and produce JSON responses.

3.4 Rule execution and testing
	•	Execution engine – Integrate or implement a DMN engine that evaluates decision tables and FEEL expressions deterministically.  Must handle default values and fallback rules.  Execution should be side‑effect free.
	•	Testing harness – Auto‑generate unit tests for each decision table and integration tests for GraphQL endpoints.  Provide a test runner and coverage reports.
	•	Explainability – Include an option to return an execution trace detailing which rules fired and why.  This trace assists debugging and auditing and is required for compliance and risk management.

3.5 Governance and versioning
	•	Version control – Each DMN model, API schema and code artifact must have a version.  Published versions are immutable; new changes create a new version.  Support branching and rollback.
	•	Audit trail – Log all prompt interactions, model changes, approvals, API calls and decisions.  Provide an API to query logs.  Keep logs for at least the retention period required by policy.
	•	Compliance – Comply with DMN standards, data privacy laws and internal development policies.  For example, DMN is designed to make decision logic readable and unambiguous ￼, which helps with regulatory compliance.

4. Non‑functional requirements
	•	Performance – DMN evaluations should return within 100 ms for simple decisions under normal load.  GraphQL and REST endpoints should handle at least 100 concurrent requests with acceptable latency.  Use caching and pre‑compiled models to reduce overhead.
	•	Scalability – Architecture must support horizontal scaling.  Store DMN models and logs in a database that supports concurrent access.
	•	Availability and reliability – Achieve at least 99.9 % uptime.  Use health checks and automatic failover for the API layer.  Persist DMN models and state in durable storage.
	•	Security – All data in transit must be encrypted (TLS).  Store secrets (e.g., LLM API keys) securely.  Sanitize inputs to prevent injection attacks.  Provide rate limiting and request throttling.
	•	Usability – The modeling UI must be intuitive for business users, with drag‑and‑drop DRD creation and spreadsheet‑like decision tables.  The system should provide clear error messages and tooltips.  Documentation should explain DMN concepts and GraphQL usage.

5. Project name rationale

The name DecisionFabric AI is chosen because it better captures the purpose of the platform:
	•	Decision – highlights that the platform revolves around modelling and executing business decisions using the DMN standard ￼.
	•	Fabric – evokes a woven structure that brings together disparate threads: DMN models, AI‑generated artifacts, GraphQL/REST APIs and governance processes.  The platform weaves these threads into a cohesive “fabric” that developers and analysts can build on.
	•	AI – signals that the development process is guided and accelerated by artificial intelligence, but with human‑controlled checkpoints.

The previous name, DMNexus AI, was a generic combination of DMN and “nexus.”  “DecisionFabric AI” more clearly communicates the weaving together of decisions, AI and APIs, and avoids reusing the overused term “nexus.”

6. Summary

DecisionFabric AI aims to simplify and accelerate the creation of DMN‑compliant decision services by combining a structured AI‑driven development process with GraphQL and REST APIs.  By adhering to DMN, the platform ensures that decision logic is precise, readable and portable.  By using GraphQL, clients can query decision services efficiently and retrieve only the data they need ￼.  By adopting an AI‑DLC with human approval gates and persistent state, the project balances innovation with governance and control.