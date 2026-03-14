# Business Logic Model — Unit 2: Rule Management

## Design Decisions Reference

| # | Question | Answer | Implication |
|---|---|---|---|
| Q1 | Rule name uniqueness | **Per rule set** | Uniqueness scoped to `(ruleSetId, name)` composite |
| Q2 | Versioning model | **Branch model** | Every `PUT /rules/{id}` creates a new DRAFT; no in-place overwrites |
| Q3 | Active version constraint | **Multiple active** | Multiple versions of the same rule may be ACTIVE simultaneously (A/B rollout) |
| Q4 | Rule sets | **Required container** | A rule cannot exist without a parent rule set |
| Q5 | DMN import granularity | **One rule per file** | Entire `.dmn` file stored as one Rule; name sourced from import parameter |
| Q6 | Immutability of activated versions | **Immutable** | ACTIVE version's DMN XML cannot be modified; must create a new version |
| Q7 | Deactivation / deletion | **Soft delete** | Status transitions to `INACTIVE`; data preserved for audit |

---

## 1. Core Business Concepts

### 1.1 Rule Set
A **RuleSet** is the mandatory organisational container for rules. Every rule must belong to exactly one rule set. Rule sets provide grouping, bulk operations, and access boundaries.

- Rule set names are **globally unique** across the system.
- A rule set can be `ACTIVE` or `INACTIVE`.
- Soft-deleting a rule set cascades to soft-delete all its contained rules.
- A rule set cannot be soft-deleted while any of its rules has a version in `ACTIVE` status; the caller must deactivate all active versions first.

### 1.2 Rule
A **Rule** is the central domain object. It represents a named, versioned unit of decision logic expressed as DMN XML.

- A rule name is **unique within its rule set** (composite uniqueness: `ruleSetId + name`).
- A rule holds an ordered collection of `RuleVersion` entities.
- A rule's own status (`ACTIVE` / `INACTIVE`) represents whether the rule is under management. An `INACTIVE` rule is soft-deleted and excluded from all normal queries.
- A rule is created with its first version in `DRAFT` state.

### 1.3 Rule Version
A **RuleVersion** captures one specific snapshot of a rule's DMN logic.

- Version numbers are monotonically increasing integers starting at `1`, assigned automatically.
- A version is always created in `DRAFT` state.
- A version transitions `DRAFT → ACTIVE` when the operator explicitly activates it.
- An `ACTIVE` version's DMN XML is **immutable** — no updates are allowed once activated.
- An `ACTIVE` version can be transitioned to `INACTIVE` by explicitly deactivating it.
- A `DRAFT` version can be discarded (transitioned to `INACTIVE`) without ever being activated.
- Multiple versions of the same rule may be in `ACTIVE` state simultaneously (A/B rollout support).

---

## 2. Lifecycle Workflows

### 2.1 Rule Lifecycle State Machine

```
                    ┌─────────────────────────────────┐
                    │           RuleSet                │
                    │  ACTIVE ──────────────► INACTIVE │
                    │   (all rules cascade INACTIVE)   │
                    └─────────────────────────────────┘

                    ┌─────────────────────────────────┐
                    │             Rule                 │
                    │  ACTIVE ──────────────► INACTIVE │
                    │  (created as ACTIVE)    (soft    │
                    │                          delete) │
                    └─────────────────────────────────┘
```

### 2.2 Rule Version State Machine

```
        [Create Rule]          [PUT /rules/{id}]
              │                       │
              ▼                       ▼
           DRAFT ──────────────────► DRAFT  (new version, monotonic increment)
              │                       │
    [activate]│               [activate]│                [discard]
              ▼                       ▼                      │
           ACTIVE ◄──────────────── ACTIVE  ◄── ... ◄────────┘ INACTIVE
              │
   [deactivate]│
              ▼
           INACTIVE
```

**Rules governing transitions:**

| From | To | Trigger | Guard |
|---|---|---|---|
| `DRAFT` | `ACTIVE` | `POST /rules/{id}/versions/{v}/activate` | DMN XML must be valid per OMG DMN 1.4; rule and rule set must be `ACTIVE` |
| `DRAFT` | `INACTIVE` | `POST /rules/{id}/versions/{v}/discard` | Version must be in `DRAFT` state |
| `ACTIVE` | `INACTIVE` | `POST /rules/{id}/versions/{v}/deactivate` | None additional |
| `ACTIVE` | `DRAFT` | *(forbidden)* | Activated versions are immutable and cannot revert |
| `INACTIVE` | *(any)* | *(forbidden)* | Deactivated versions are terminal; create a new DRAFT instead |

---

### 2.3 Create Rule Workflow

```
Caller (RULE_ADMIN) → POST /api/v1/rules
  │
  ├─ Validate: ruleSetId exists and is ACTIVE
  ├─ Validate: DMN XML against OMG DMN 1.4 schema
  ├─ Validate: name unique within rule set
  │
  ├─ Create Rule (status=ACTIVE)
  ├─ Create RuleVersion v1 (status=DRAFT, dmnXml=provided XML)
  ├─ Persist to PostgreSQL
  ├─ Publish RuleLifecycleEvent(CREATED)
  └─ Return RuleResponse (id, name, currentVersion=1, status=ACTIVE)
```

### 2.4 Update Rule Workflow (Branch Model)

```
Caller (RULE_ADMIN) → PUT /api/v1/rules/{id}
  │
  ├─ Validate: rule exists and is ACTIVE (not soft-deleted)
  ├─ Validate: new DMN XML against OMG DMN 1.4 schema
  │
  ├─ Derive next version number = max(existing versions) + 1
  ├─ Create new RuleVersion vN (status=DRAFT, dmnXml=new XML)
  ├─ Update rule.updatedAt; optionally update name/description
  │    (name change validated for uniqueness within rule set)
  ├─ Persist to PostgreSQL
  ├─ Publish RuleLifecycleEvent(UPDATED)
  └─ Return RuleResponse (id, latestDraftVersion=N, ...)
```

> **Key invariant**: An existing ACTIVE version is never touched. The old ACTIVE version continues to serve evaluations while the new DRAFT is under review.

### 2.5 Activate Version Workflow

```
Caller (RULE_ADMIN) → POST /api/v1/rules/{id}/versions/{v}/activate
  │
  ├─ Validate: rule exists and is ACTIVE
  ├─ Validate: version vN exists and is in DRAFT state
  ├─ Validate: DMN XML is valid (idempotent re-check)
  │
  ├─ Set version vN status = ACTIVE
  │   (NOTE: other previously ACTIVE versions remain ACTIVE — no auto-deactivation)
  ├─ Persist to PostgreSQL
  ├─ Publish RuleLifecycleEvent(ACTIVATED, ruleId, version=N)
  ├─ InMemoryRuleCache reloads active versions for this ruleId
  └─ Return RuleVersionResponse (version=N, status=ACTIVE, activatedAt=...)
```

### 2.6 Deactivate Version Workflow

```
Caller (RULE_ADMIN) → POST /api/v1/rules/{id}/versions/{v}/deactivate
  │
  ├─ Validate: rule exists and is ACTIVE
  ├─ Validate: version vN exists and is ACTIVE
  │
  ├─ Set version vN status = INACTIVE
  ├─ Persist to PostgreSQL
  ├─ Publish RuleLifecycleEvent(DEACTIVATED, ruleId, version=N)
  ├─ InMemoryRuleCache removes this version for this ruleId
  └─ Return RuleVersionResponse (version=N, status=INACTIVE)
```

### 2.7 Soft-Delete Rule Workflow

```
Caller (RULE_ADMIN) → DELETE /api/v1/rules/{id}
  │
  ├─ Validate: rule exists
  ├─ Set rule.status = INACTIVE
  ├─ All ACTIVE versions for this rule → INACTIVE (cascade)
  ├─ Persist to PostgreSQL
  ├─ Publish RuleLifecycleEvent(DELETED, ruleId)
  ├─ InMemoryRuleCache evicts all versions for this ruleId
  └─ Return 204 No Content
```

### 2.8 DMN Import Workflow

```
Caller (RULE_ADMIN) → POST /api/v1/rules/import  (multipart .dmn file)
  │
  ├─ Validate: file is valid UTF-8 XML
  ├─ Validate: XML parses as valid OMG DMN 1.4 document
  ├─ Extract: rule name from request parameter `ruleName` (required)
  ├─ Validate: ruleSetId in request body exists and is ACTIVE
  ├─ Validate: name unique within rule set
  │
  ├─ Create Rule (status=ACTIVE, name=ruleName, ruleSetId=provided)
  ├─ Create RuleVersion v1 (status=DRAFT, dmnXml=file content)
  ├─ Persist to PostgreSQL
  ├─ Publish RuleLifecycleEvent(CREATED)
  └─ Return DmnImportResponse (ruleId, version=1, status=DRAFT)
```

> The entire `.dmn` file is stored verbatim as one rule. Rule name is supplied by the caller (not extracted from the DMN namespace).

### 2.9 DMN Export Workflow

```
Caller (RULE_ADMIN | RULE_READER) → GET /api/v1/rules/{id}/export?version={v}
  │
  ├─ Validate: rule exists (including INACTIVE rules for audit access)
  │   (RULE_READER cannot export INACTIVE rules — returns 404)
  ├─ If version param absent: return highest version number
  ├─ If version param present: return that specific version
  │
  └─ Return raw DMN XML as Content-Type: application/xml attachment
```

### 2.10 Rule Set Management Workflow

```
Create:  POST /api/v1/rule-sets   → validate name uniqueness globally → persist
List:    GET  /api/v1/rule-sets   → return paginated list (ACTIVE only by default)
Get:     GET  /api/v1/rule-sets/{id} → return rule set + rule count
Delete:  DELETE /api/v1/rule-sets/{id}
  │
  ├─ Validate: no rules have ACTIVE versions in this rule set
  │   (reject with 409 Conflict if any ACTIVE version found)
  ├─ Set ruleSet.status = INACTIVE
  ├─ Cascade: set all rules in rule set status = INACTIVE
  └─ Return 204 No Content
```

---

## 3. Cache Integration

The `InMemoryRuleCache` maintains a snapshot of all **ACTIVE version DMN XML** keyed by `ruleId`. It is used by Unit 3 (Decision Evaluation) to avoid database reads on the hot evaluation path.

### Cache Invalidation / Reload Triggers

| Event | Cache Action |
|---|---|
| `RuleLifecycleEvent.ACTIVATED` | Add/replace version entry for `ruleId` |
| `RuleLifecycleEvent.DEACTIVATED` | Remove that specific version entry for `ruleId` |
| `RuleLifecycleEvent.DELETED` | Evict all version entries for `ruleId` |
| `RuleLifecycleEvent.CREATED` | No action (new rules start as DRAFT) |
| Application startup | Warm-up: load all ACTIVE versions from DB |

The cache stores `Map<RuleId, List<CachedRuleVersion>>` where `CachedRuleVersion` holds `(version, dmnXml, activatedAt)`. A rule can have multiple entries if multiple versions are ACTIVE simultaneously.

---

## 4. API Surface Summary

### Rule Set APIs

| Method | Path | Role Required | Description |
|---|---|---|---|
| `POST` | `/api/v1/rule-sets` | `RULE_ADMIN` | Create a new rule set |
| `GET` | `/api/v1/rule-sets` | `RULE_READER` | List rule sets (paginated) |
| `GET` | `/api/v1/rule-sets/{id}` | `RULE_READER` | Get rule set by ID |
| `DELETE` | `/api/v1/rule-sets/{id}` | `RULE_ADMIN` | Soft-delete rule set |

### Rule APIs

| Method | Path | Role Required | Description |
|---|---|---|---|
| `POST` | `/api/v1/rules` | `RULE_ADMIN` | Create rule (v1 as DRAFT) |
| `GET` | `/api/v1/rules` | `RULE_READER` | List rules (paginated, filterable by rule set) |
| `GET` | `/api/v1/rules/{id}` | `RULE_READER` | Get rule with version list |
| `PUT` | `/api/v1/rules/{id}` | `RULE_ADMIN` | Create new DRAFT version (branch model) |
| `POST` | `/api/v1/rules/{id}/versions/{v}/activate` | `RULE_ADMIN` | Activate a specific version |
| `POST` | `/api/v1/rules/{id}/versions/{v}/deactivate` | `RULE_ADMIN` | Deactivate a specific active version |
| `POST` | `/api/v1/rules/{id}/versions/{v}/discard` | `RULE_ADMIN` | Discard a DRAFT version |
| `DELETE` | `/api/v1/rules/{id}` | `RULE_ADMIN` | Soft-delete rule (cascades versions) |
| `POST` | `/api/v1/rules/import` | `RULE_ADMIN` | Import `.dmn` file as new rule |
| `GET` | `/api/v1/rules/{id}/export` | `RULE_READER` | Export rule's DMN XML |
| `POST` | `/api/v1/rules/validate` | `RULE_ADMIN` | Validate DMN XML without persisting |

---

## 5. Audit Trail

Every lifecycle state transition must produce an entry in the `rule_audit_log` table (separate from the decision audit):

| Event | Fields Logged |
|---|---|
| `CREATED` | `ruleId`, `ruleSetId`, `userId`, `correlationId`, `timestamp`, `action=CREATE` |
| `UPDATED` (new version) | `ruleId`, `version`, `userId`, `correlationId`, `timestamp`, `action=VERSION_CREATED` |
| `ACTIVATED` | `ruleId`, `version`, `userId`, `correlationId`, `timestamp`, `action=ACTIVATE` |
| `DEACTIVATED` | `ruleId`, `version`, `userId`, `correlationId`, `timestamp`, `action=DEACTIVATE` |
| `DELETED` | `ruleId`, `userId`, `correlationId`, `timestamp`, `action=DELETE` |
| `IMPORTED` | `ruleId`, `ruleSetId`, `userId`, `correlationId`, `timestamp`, `action=IMPORT`, `fileName` |

> `userId` and `correlationId` are extracted from the MDC context (JWT + CorrelationIdFilter).
