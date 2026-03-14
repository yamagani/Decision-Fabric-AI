# Decision Fabric AI

A DMN-compliant business rules engine with REST API, JWT-based security (Keycloak), and full Docker Compose support.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Running Locally (without Docker)](#running-locally-without-docker)
- [API Reference](#api-reference)
- [Demo Walkthrough](#demo-walkthrough)
- [Running Tests](#running-tests)
- [User Roles](#user-roles)
- [Configuration](#configuration)
- [Useful Commands](#useful-commands)

---

## Overview

Decision Fabric AI combines a Decision Model and Notation (DMN) rules engine with a structured REST API and AI-driven development lifecycle (AI-DLC). It allows business analysts to design decision logic in DMN, manage rule sets and versioned rules, and expose them through a governed, role-secured API.

**Tech stack:**

| Component       | Technology                          |
|-----------------|-------------------------------------|
| Language        | Kotlin 1.9.22 / Java 21             |
| Framework       | Spring Boot 3.2.3                   |
| Database        | PostgreSQL 15                       |
| Auth / Identity | Keycloak 24.0 (OIDC / JWT)          |
| DMN Engine      | Drools (KIE)                        |
| Build tool      | Gradle (Kotlin DSL)                 |
| Container       | Docker + Docker Compose             |
| API tests       | Karate 1.5.1                        |

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                    Docker Compose                    │
│                                                     │
│  ┌──────────────┐  ┌───────────────┐  ┌──────────┐ │
│  │  PostgreSQL  │  │   Keycloak    │  │  App     │ │
│  │  :5432       │  │   :9090       │  │  :8080   │ │
│  └──────────────┘  └───────────────┘  └──────────┘ │
└─────────────────────────────────────────────────────┘

Client → GET /api/v1/rules  (Bearer JWT)
       ↓
  Spring Security (JWKS validation via Keycloak)
       ↓
  RuleApiController → Application Layer → JPA → PostgreSQL
```

The app follows a clean hexagonal (ports & adapters) architecture:

- **Inbound adapters** — REST controllers (`/api/v1/rules`, `/api/v1/rule-sets`)
- **Application layer** — use cases, command/query handlers
- **Domain** — `Rule`, `RuleSet`, versioning, DMN validation logic
- **Outbound adapters** — JPA (PostgreSQL), Drools DMN validator

---

## Prerequisites

- **Docker Desktop** (with Compose v2) — [install](https://docs.docker.com/get-docker/)
- **Java 21** (only needed for local Gradle runs, not for Docker) — e.g. `brew install --cask temurin@21`
- `jq` for pretty-printing JSON in the demo (`brew install jq`)
- `curl` (pre-installed on macOS/Linux)

---

## Quick Start

The fastest way to run everything (build, test, and start all services):

```bash
git clone https://github.com/swag2006/Decision-Fabric-AI.git
cd Decision-Fabric-AI
./run-local.sh
```

This script will:
1. Build the Gradle project and run unit/slice tests
2. Build the Docker image (`decision-fabric-ai:local`)
3. Start three containers: `postgres`, `keycloak`, `app`
4. Wait for the app to become healthy

Once complete, services are available at:

| Service      | URL                                               | Credentials          |
|--------------|---------------------------------------------------|----------------------|
| App API      | http://localhost:8080                             | (JWT required)       |
| Health check | http://localhost:8080/actuator/health             | —                    |
| Keycloak     | http://localhost:9090                             | admin / admin        |
| PostgreSQL   | localhost:5432, DB: `decisionfabric`              | decisionfabric / decisionfabric |

### Skip tests (faster startup)

```bash
./run-local.sh --skip-tests
```

### Tear down everything (including volumes)

```bash
./run-local.sh --down
```

### Tail app logs

```bash
./run-local.sh --logs
```

---

## Running Locally (without Docker)

If you want to run the Spring Boot app directly (e.g. for IDE debugging), you still need Postgres and Keycloak running. Start just those:

```bash
docker compose up -d postgres keycloak
```

Then run the app with Java 21:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
./gradlew bootRun --args='--spring.profiles.active=local'
```

The `local` profile connects to `localhost:5432` and `localhost:9090` (matching the Compose ports).

---

## API Reference

All endpoints are under `/api/v1` and require a valid Bearer JWT (except `/actuator/health`).

### Rule Sets

| Method   | Path                   | Role required   | Description                        |
|----------|------------------------|-----------------|------------------------------------|
| `POST`   | `/api/v1/rule-sets`    | `RULE_ADMIN`    | Create a new rule set              |
| `GET`    | `/api/v1/rule-sets`    | `RULE_READER`   | List all rule sets                 |
| `GET`    | `/api/v1/rule-sets/{id}` | `RULE_READER` | Get a rule set by ID               |
| `DELETE` | `/api/v1/rule-sets/{id}` | `RULE_ADMIN`  | Delete a rule set                  |

### Rules

| Method   | Path                                            | Role required     | Description                          |
|----------|-------------------------------------------------|-------------------|--------------------------------------|
| `POST`   | `/api/v1/rules`                                 | `RULE_ADMIN`      | Create a rule (upload DMN XML)       |
| `GET`    | `/api/v1/rules`                                 | `RULE_READER`     | Search/list rules (paginated)        |
| `GET`    | `/api/v1/rules/{id}`                            | `RULE_READER`     | Get a rule by ID                     |
| `PUT`    | `/api/v1/rules/{id}`                            | `RULE_ADMIN`      | Update a rule (creates new version)  |
| `DELETE` | `/api/v1/rules/{id}`                            | `RULE_ADMIN`      | Delete a rule                        |
| `POST`   | `/api/v1/rules/{id}/versions/{v}/activate`      | `RULE_ADMIN`      | Activate a specific version          |
| `POST`   | `/api/v1/rules/{id}/versions/{v}/deactivate`    | `RULE_ADMIN`      | Deactivate a specific version        |
| `POST`   | `/api/v1/rules/{id}/versions/{v}/discard`       | `RULE_ADMIN`      | Discard (soft-delete) a version      |
| `DELETE` | `/api/v1/rules/{id}/versions/{v}`               | `RULE_ADMIN`      | Hard-delete a version                |
| `POST`   | `/api/v1/rules/import`                          | `RULE_ADMIN`      | Import DMN file (multipart/form-data)|
| `GET`    | `/api/v1/rules/{id}/export`                     | `RULE_READER`     | Export rule as DMN XML               |
| `POST`   | `/api/v1/rules/validate`                        | `RULE_READER`     | Validate DMN XML (Content-Type: `application/xml`) |

---

## Demo Walkthrough

### 1. Get an access token

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:9090/realms/decision-fabric-ai/protocol/openid-connect/token \
  -d 'client_id=decision-fabric-ai-client' \
  -d 'client_secret=local-secret' \
  -d 'grant_type=password' \
  -d 'username=rule-admin' \
  -d 'password=password' \
  | jq -r .access_token)

echo "Token: ${TOKEN:0:40}..."
```

### 2. Create a Rule Set

```bash
RULE_SET=$(curl -s -X POST http://localhost:8080/api/v1/rule-sets \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "Loan Approval Rules", "description": "Rules for automated loan decisions"}' \
  | tee /dev/stderr | jq .)

RULE_SET_ID=$(echo "$RULE_SET" | jq -r .id)
echo "Created rule set: $RULE_SET_ID"
```

### 3. Validate a DMN document

```bash
curl -s -X POST http://localhost:8080/api/v1/rules/validate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/xml" \
  -d '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
             xmlns:dmndi="https://www.omg.org/spec/DMN/20191111/DMNDI/"
             id="loan-approval" name="Loan Approval"
             namespace="http://camunda.org/schema/1.0/dmn">
  <decision id="loanDecision" name="Loan Decision">
    <decisionTable id="loanTable" hitPolicy="UNIQUE">
      <input id="input1" label="Credit Score">
        <inputExpression id="inputExpr1" typeRef="integer">
          <text>creditScore</text>
        </inputExpression>
      </input>
      <output id="output1" label="Decision" name="decision" typeRef="string"/>
      <rule id="rule1">
        <inputEntry id="ie1"><text>&gt;= 700</text></inputEntry>
        <outputEntry id="oe1"><text>"APPROVE"</text></outputEntry>
      </rule>
      <rule id="rule2">
        <inputEntry id="ie2"><text>&lt; 700</text></inputEntry>
        <outputEntry id="oe2"><text>"REJECT"</text></outputEntry>
      </rule>
    </decisionTable>
  </decision>
</definitions>' | jq .
```

Expected response:
```json
{
  "valid": true,
  "errors": []
}
```

### 4. Create a Rule

```bash
RULE=$(curl -s -X POST http://localhost:8080/api/v1/rules \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"Loan Approval\",
    \"description\": \"Approves or rejects loans based on credit score\",
    \"ruleSetId\": \"$RULE_SET_ID\",
    \"dmnContent\": \"<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?><definitions xmlns=\\\"https://www.omg.org/spec/DMN/20191111/MODEL/\\\" id=\\\"loan\\\" name=\\\"Loan\\\" namespace=\\\"http://camunda.org/schema/1.0/dmn\\\"><decision id=\\\"d1\\\" name=\\\"Decision\\\"><decisionTable id=\\\"dt1\\\" hitPolicy=\\\"UNIQUE\\\"><input id=\\\"i1\\\" label=\\\"Score\\\"><inputExpression id=\\\"ie1\\\" typeRef=\\\"integer\\\"><text>score</text></inputExpression></input><output id=\\\"o1\\\" label=\\\"Result\\\" name=\\\"result\\\" typeRef=\\\"string\\\"/><rule id=\\\"r1\\\"><inputEntry id=\\\"ine1\\\"><text>&gt;= 700</text></inputEntry><outputEntry id=\\\"one1\\\"><text>\\\"APPROVE\\\"</text></outputEntry></rule></decisionTable></decision></definitions>\"
  }" | jq .)

RULE_ID=$(echo "$RULE" | jq -r .id)
echo "Created rule: $RULE_ID"
```

### 5. List and search rules

```bash
# List all rules
curl -s "http://localhost:8080/api/v1/rules" \
  -H "Authorization: Bearer $TOKEN" | jq .

# Search by name
curl -s "http://localhost:8080/api/v1/rules?search=Loan" \
  -H "Authorization: Bearer $TOKEN" | jq .

# Filter by rule set
curl -s "http://localhost:8080/api/v1/rules?ruleSetId=$RULE_SET_ID" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### 6. Activate a rule version

```bash
curl -s -X POST "http://localhost:8080/api/v1/rules/$RULE_ID/versions/1/activate" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### 7. Export a rule as DMN XML

```bash
curl -s "http://localhost:8080/api/v1/rules/$RULE_ID/export" \
  -H "Authorization: Bearer $TOKEN"
```

### 8. Test role-based access (403 expected)

```bash
# Get a consumer token (DECISION_CONSUMER role only)
CONSUMER_TOKEN=$(curl -s -X POST \
  http://localhost:9090/realms/decision-fabric-ai/protocol/openid-connect/token \
  -d 'client_id=decision-fabric-ai-client' \
  -d 'client_secret=local-secret' \
  -d 'grant_type=password' \
  -d 'username=consumer' \
  -d 'password=password' \
  | jq -r .access_token)

# Try to create a rule set — should return 403
curl -s -X POST http://localhost:8080/api/v1/rule-sets \
  -H "Authorization: Bearer $CONSUMER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "Should Fail"}' | jq .
```

---

## Running Tests

### Unit & slice tests (no Docker required)

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
./gradlew test \
  --tests "com.decisionfabric.domain.*" \
  --tests "com.decisionfabric.application.*" \
  --tests "com.decisionfabric.adapter.inbound.*" \
  --tests "com.decisionfabric.adapter.outbound.cache.*"
```

Test report: `build/reports/tests/test/index.html`

### Karate API tests (requires running Docker stack)

```bash
# Start the stack first (if not already running)
./run-local.sh --skip-tests

# Run all 22 Karate scenarios
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
./gradlew karateTest
```

Karate HTML report: `build/karate-reports/karate-summary.html`

The Karate suite covers:
- **rule-sets.feature** — 9 scenarios: CRUD operations, uniqueness validation, role-based access (403)
- **rules.feature** — 13 scenarios: CRUD, versioning (activate/deactivate/discard), DMN validate, import/export, role-based access (403)

---

## User Roles

Three demo users are pre-configured in Keycloak (password: `password` for all):

| Username       | Roles                                                         | Can do                                      |
|----------------|---------------------------------------------------------------|---------------------------------------------|
| `rule-admin`   | `RULE_ADMIN`, `RULE_READER`                                   | Full CRUD on rules and rule sets            |
| `system-admin` | `SYSTEM_ADMIN`, `RULE_ADMIN`, `RULE_READER`, `AUDIT_READER`, `DECISION_CONSUMER` | All operations |
| `consumer`     | `DECISION_CONSUMER`                                           | Read-only; cannot create or modify rules    |

Role hierarchy:
- `RULE_ADMIN` — create, update, delete, version management
- `RULE_READER` — read rules, rule sets, validate DMN
- `DECISION_CONSUMER` — invoke decision services
- `AUDIT_READER` — access audit logs
- `SYSTEM_ADMIN` — administrative operations

---

## Configuration

Key environment variables (set in `docker-compose.yml` for Docker; override in `application-local.yml` for local runs):

| Variable                                                    | Default (Docker)                                                    | Description                        |
|-------------------------------------------------------------|---------------------------------------------------------------------|------------------------------------|
| `SPRING_DATASOURCE_URL`                                     | `jdbc:postgresql://postgres:5432/decisionfabric`                   | Database URL                       |
| `SPRING_DATASOURCE_USERNAME`                                | `decisionfabric`                                                    | DB username                        |
| `SPRING_DATASOURCE_PASSWORD`                                | `decisionfabric`                                                    | DB password                        |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI`      | `http://localhost:9090/realms/decision-fabric-ai`                   | JWT issuer (for `iss` claim check) |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI`     | `http://keycloak:8080/realms/decision-fabric-ai/protocol/openid-connect/certs` | JWKS endpoint |
| `DMN_ENGINE_PROVIDER`                                       | `drools`                                                            | DMN engine (`drools`)              |
| `RULE_CACHE_MAX_BYTES_MB`                                   | `200`                                                               | In-memory rule cache size          |
| `SPRING_PROFILES_ACTIVE`                                    | `local`                                                             | Spring profile                     |

---

## Useful Commands

```bash
# Start all services (build + test + docker up)
./run-local.sh

# Start all services, skip Gradle tests
./run-local.sh --skip-tests

# Build only (no compose up)
./run-local.sh --build-only

# Tail app logs
./run-local.sh --logs

# Stop all containers and remove volumes
./run-local.sh --down

# Re-build Docker image from scratch (no cache)
docker build --no-cache --target runtime -t decision-fabric-ai:local .

# View running containers
docker compose ps

# View app logs
docker compose logs -f app

# Connect to PostgreSQL
docker exec -it decisionfabric-postgres psql -U decisionfabric -d decisionfabric

# Run all Karate API tests
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
./gradlew karateTest

# Open Karate HTML report
open build/karate-reports/karate-summary.html

# Full clean build
./gradlew clean build -x test
```
