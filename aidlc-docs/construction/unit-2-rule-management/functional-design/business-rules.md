# Business Rules — Unit 2: Rule Management

## Overview
This document captures all business rules, validation constraints, invariants, and guard conditions that govern the Rule Management domain. Rules are grouped by concern and include the error response when violated.

---

## 1. Rule Set Rules

### BR-RS-01: Global Name Uniqueness
**Statement**: A rule set's name must be unique across all rule sets in the system, regardless of status.  
**Check**: Before creating a rule set, query the repository for any existing rule set with the same `name` (case-insensitive, trimmed).  
**Violation Response**: `409 Conflict` — `"A rule set with name '{name}' already exists."`

### BR-RS-02: Non-Empty Name
**Statement**: A rule set name must be between 1 and 255 characters and contain at least one non-whitespace character.  
**Check**: Validate at command ingestion.  
**Violation Response**: `422 Unprocessable Entity` — `"Rule set name must be between 1 and 255 characters."`

### BR-RS-03: Delete Guard — No Active Versions
**Statement**: A rule set cannot be soft-deleted if any rule within it has at least one version in `ACTIVE` status.  
**Check**: Before deleting a rule set, query `rule_versions` for any row where `rule.rule_set_id = :id AND rule_versions.status = 'ACTIVE'`.  
**Violation Response**: `409 Conflict` — `"Rule set '{id}' cannot be deleted while it contains rules with active versions. Deactivate all active versions first."`  
**Note**: The caller must explicitly deactivate all active versions; the system does not cascade-deactivate automatically at rule-set deletion.

### BR-RS-04: Cannot Reactivate a Deleted Rule Set
**Statement**: A rule set with `status=INACTIVE` cannot be updated or transitioned back to `ACTIVE` through the standard API.  
**Check**: Any mutating operation on an INACTIVE rule set returns not-found.  
**Violation Response**: `404 Not Found` (INACTIVE rule sets are excluded from normal queries)

---

## 2. Rule Creation Rules

### BR-RC-01: Rule Set Must Exist and Be Active
**Statement**: A rule can only be created inside an existing `ACTIVE` rule set.  
**Check**: Load rule set by `ruleSetId`; verify `status == ACTIVE`.  
**Violation Response**: `404 Not Found` if rule set does not exist; `422 Unprocessable Entity` — `"Rule set '{ruleSetId}' is not active."` if INACTIVE

### BR-RC-02: Name Unique Within Rule Set
**Statement**: A rule's name must be unique within its rule set; the same name may be used in different rule sets.  
**Check**: Before creating, query for existing rule where `rule_set_id = :ruleSetId AND LOWER(name) = LOWER(:name)`.  
**Violation Response**: `409 Conflict` — `"A rule named '{name}' already exists in rule set '{ruleSetId}'."`

### BR-RC-03: DMN XML Must Be Valid
**Statement**: The `dmnXml` provided at creation must be a well-formed, OMG DMN 1.4-compliant XML document.  
**Check**: Invoke `DmnSchemaValidatorPort.validate(dmnXml)` before persisting; reject if `ValidationResult.isValid == false`.  
**Violation Response**: `422 Unprocessable Entity` — `"DMN validation failed: {list of errors from ValidationResult.errors}"`

### BR-RC-04: Initial Version Is Always DRAFT
**Statement**: The first version created alongside a new rule always has `status=DRAFT`. It is never automatically activated.  
**Check**: Enforced by domain model; `addVersion()` always creates `status=DRAFT`.  
**Violation Response**: N/A (invariant, not user-facing)

### BR-RC-05: Initial Version Number Is 1
**Statement**: The first version of any rule has `version=1`.  
**Check**: Enforced by `nextVersionNumber()` when `versions` is empty.  
**Violation Response**: N/A (invariant)

---

## 3. Rule Update Rules (Branch Model)

### BR-RU-01: Rule Must Exist and Be Active
**Statement**: `PUT /rules/{id}` requires the target rule to exist with `status=ACTIVE`.  
**Check**: Load rule by `ruleId`; verify `status == ACTIVE`.  
**Violation Response**: `404 Not Found`

### BR-RU-02: Every PUT Creates a New DRAFT Version
**Statement**: Updating a rule never modifies an existing version in-place. A new `RuleVersion` is always created in `DRAFT` status with `version = max(existing) + 1`.  
**Check**: Enforced by domain method `Rule.addVersion()`; no in-place update path exists.  
**Violation Response**: N/A (design invariant)

### BR-RU-03: New Version DMN XML Must Be Valid
**Statement**: The updated `dmnXml` must pass OMG DMN 1.4 schema validation.  
**Check**: Same as BR-RC-03.  
**Violation Response**: `422 Unprocessable Entity` — same as BR-RC-03

### BR-RU-04: Name Change Must Preserve Uniqueness Within Rule Set
**Statement**: If the `name` field is included in an update command, the new name must still be unique within the rule's rule set (excluding the rule being updated itself).  
**Check**: If `name` is changing, query for `rule_set_id = :ruleSetId AND LOWER(name) = LOWER(:newName) AND id != :ruleId`.  
**Violation Response**: `409 Conflict` — same as BR-RC-02

### BR-RU-05: DRAFT Version Accumulation Allowed
**Statement**: There is no maximum number of DRAFT versions per rule. Multiple un-activated DRAFT versions may coexist. This is intentional to support parallel branch development.  
**Violation Response**: N/A (no limit enforced)

---

## 4. Version Activation Rules

### BR-VA-01: Version Must Exist and Be DRAFT
**Statement**: Only a version in `DRAFT` status can be activated.  
**Check**: Load rule; find version with matching `version` number; verify `status == DRAFT`.  
**Violation Response**: `404 Not Found` if version does not exist; `422 Unprocessable Entity` — `"Version {v} of rule '{id}' is not in DRAFT status (current status: {actual})."`

### BR-VA-02: Rule Must Be Active
**Statement**: A version cannot be activated on a rule with `status=INACTIVE`.  
**Check**: Verify `rule.status == ACTIVE` before activation.  
**Violation Response**: `404 Not Found` (INACTIVE rules are excluded from normal queries)

### BR-VA-03: No Auto-Deactivation of Previous Active Versions
**Statement**: Activating version N does **not** automatically deactivate previously ACTIVE versions. Multiple versions of the same rule may be simultaneously `ACTIVE`.  
**Check**: Enforced by `Rule.activateVersion()` — only the target version's status changes.  
**Violation Response**: N/A (design decision / A/B rollout support)

### BR-VA-04: Activation Re-validates DMN XML
**Statement**: DMN schema validation is re-run at activation time to guard against any edge case where a stored DRAFT XML is no longer considered valid (e.g., schema upgrade).  
**Check**: Call `DmnSchemaValidatorPort.validate()` again during `ActivateVersionCommand` processing.  
**Violation Response**: `422 Unprocessable Entity` — same as BR-RC-03

### BR-VA-05: Activated Version Is Immutable
**Statement**: Once `status=ACTIVE`, the `dmnXml` field of a `RuleVersion` cannot be changed. Any attempt to update that version is rejected.  
**Check**: `Rule.activateVersion()` transitions status; subsequent `addVersion()` calls always create a new DRAFT (not modify the ACTIVE one). The repository adapter must not expose an update path for ACTIVE versions' `dmn_xml`.  
**Violation Response**: N/A (no API operation allows updating an ACTIVE version's XML directly)

---

## 5. Version Deactivation Rules

### BR-VD-01: Version Must Be Active
**Statement**: Only a version in `ACTIVE` status can be deactivated.  
**Check**: Verify `ruleVersion.status == ACTIVE`.  
**Violation Response**: `422 Unprocessable Entity` — `"Version {v} of rule '{id}' is not ACTIVE (current status: {actual})."`

### BR-VD-02: Deactivation Does Not Delete Data
**Statement**: Deactivating a version sets `status=INACTIVE`. The DMN XML and all metadata are preserved.  
**Violation Response**: N/A (invariant)

### BR-VD-03: Cache Must Be Updated on Deactivation
**Statement**: After deactivating a version, `RuleLifecycleEvent.RuleVersionDeactivated` must be published so the `InMemoryRuleCache` removes that version from the hot path.  
**Violation Response**: N/A (internal consistency requirement)

---

## 6. Version Discard Rules

### BR-VDC-01: Only DRAFT Versions Can Be Discarded
**Statement**: A version can only be discarded (moved to `INACTIVE` without activation) if it is in `DRAFT` status.  
**Check**: Verify `ruleVersion.status == DRAFT`.  
**Violation Response**: `422 Unprocessable Entity` — `"Version {v} cannot be discarded because it is not in DRAFT status (current status: {actual})."`

---

## 7. Rule Soft-Delete Rules

### BR-RD-01: Soft-Delete Sets Rule to INACTIVE
**Statement**: Deleting a rule via `DELETE /rules/{id}` sets `rule.status=INACTIVE` and cascades all `ACTIVE` versions of that rule to `INACTIVE`. `DRAFT` versions also become `INACTIVE`.  
**Check**: No guard on current status (idempotent if already INACTIVE).  
**Violation Response**: `404 Not Found` if rule does not exist at all

### BR-RD-02: Data Retained on Soft-Delete
**Statement**: A soft-deleted rule and all its versions remain in the database. They are excluded from standard paginated list queries but retrievable via audit or history queries.  
**Violation Response**: N/A (invariant)

### BR-RD-03: Cache Eviction on Delete
**Statement**: When a rule is soft-deleted, all entries for that `ruleId` in `InMemoryRuleCache` must be evicted synchronously before the API response is returned.  
**Violation Response**: N/A (internal consistency requirement)

---

## 8. DMN Import Rules

### BR-DI-01: File Must Be Valid OMG DMN 1.4 XML
**Statement**: The uploaded `.dmn` file must be parseable XML and conform to the OMG DMN 1.4 schema.  
**Check**: Validate using `DmnSchemaValidatorPort`.  
**Violation Response**: `422 Unprocessable Entity` — `"DMN schema validation failed: {errors}"`

### BR-DI-02: File Size Limit
**Statement**: Uploaded `.dmn` files must not exceed 1 MB.  
**Check**: Validated at controller layer via Spring multipart configuration.  
**Violation Response**: `413 Payload Too Large` — `"DMN file size exceeds the maximum of 1 MB."`

### BR-DI-03: Rule Name Required on Import
**Statement**: The `ruleName` request parameter is mandatory when importing a DMN file. It is used as the `Rule.name` — the name is not extracted from the DMN XML namespace.  
**Check**: Validated at controller layer (`@NotBlank`).  
**Violation Response**: `400 Bad Request` — `"ruleName parameter is required."`

### BR-DI-04: Imported Rule Follows Standard Creation Rules
**Statement**: An imported rule is subject to the same constraints as a created rule: `ruleSetId` required and ACTIVE (BR-RC-01), name unique within rule set (BR-RC-02), initial version is DRAFT (BR-RC-04).  
**Violation Response**: Same as corresponding BR-RC rules

### BR-DI-05: Whole File = One Rule
**Statement**: The entire `.dmn` file is stored as a single `Rule` record even if the file contains multiple `<decision>` elements. There is no per-decision splitting.  
**Violation Response**: N/A (design decision — documented for callers)

---

## 9. DMN Export Rules

### BR-DE-01: Export Requires Rule to Exist
**Statement**: Export returns `404 Not Found` if the rule does not exist or if the caller does not have at least `RULE_READER` role.  
**Check**: Existence check; role enforced by `SecurityFilterChain`.  
**Violation Response**: `404 Not Found` or `403 Forbidden`

### BR-DE-02: INACTIVE Rules Not Exportable by RULE_READER
**Statement**: A `RULE_READER` cannot export a soft-deleted (`INACTIVE`) rule. `RULE_ADMIN` may export for audit purposes.  
**Check**: If rule `status=INACTIVE` and caller role is `RULE_READER`, return 404.  
**Violation Response**: `404 Not Found`

### BR-DE-03: Version Parameter Defaults to Latest
**Statement**: If no `?version=` query parameter is supplied, the export returns the DMN XML of the highest version number regardless of that version's status.  
**Violation Response**: N/A (default behaviour)

### BR-DE-04: Specific Version Must Exist
**Statement**: If a `?version=N` parameter is supplied, that version must exist for the rule.  
**Violation Response**: `404 Not Found` — `"Version {N} not found for rule '{id}'."`

---

## 10. DMN Validation (Standalone) Rules

### BR-DV-01: Validate Without Side Effects
**Statement**: `POST /api/v1/rules/validate` must not persist anything to the database or publish events. It is a pure validation check.  
**Check**: Only `DmnSchemaValidatorPort.validate()` is called; no repository operations.  
**Violation Response**: Returns `200 OK` with `{ "valid": false, "errors": [...] }` — never `422` for this endpoint

---

## 11. Role-Based Access Rules

### BR-RBAC-01: RULE_ADMIN Role Required for Write Operations
**Statement**: All mutating operations (create, update, activate, deactivate, discard, delete, import) require the `RULE_ADMIN` role.  
**Check**: Enforced by `SecurityFilterChain` path matchers.  
**Violation Response**: `403 Forbidden`

### BR-RBAC-02: RULE_READER Role Sufficient for Read Operations
**Statement**: `GET` operations (list, get, export) and `POST /validate` are accessible to users with at least `RULE_READER` role.  
**Check**: Enforced by `SecurityFilterChain`.  
**Violation Response**: `403 Forbidden`

### BR-RBAC-03: Unauthenticated Requests Rejected
**Statement**: All `Rule` and `RuleSet` endpoints require a valid JWT bearer token.  
**Check**: Enforced by `SecurityFilterChain` (no permit-all paths for rule endpoints).  
**Violation Response**: `401 Unauthorized`

---

## 12. Pagination and Filtering Rules

### BR-PF-01: Default Page Size
**Statement**: Paginated list endpoints (`GET /rules`, `GET /rule-sets`) default to `page=0, size=20` if not specified.  
**Check**: Enforced at controller with `@PageableDefault(size = 20, sort = ["name"])`.

### BR-PF-02: Maximum Page Size
**Statement**: The maximum allowed `size` parameter is `100`. Requests with `size > 100` are normalised to `100`.  
**Check**: Enforced in `RuleManagementService` before delegating to repository.

### BR-PF-03: INACTIVE Rules Excluded by Default
**Statement**: Standard list queries return only rules and rule sets with `status=ACTIVE` unless `?includeInactive=true` is explicitly passed by a `RULE_ADMIN`.  
**Check**: Repository query includes `status` filter; `includeInactive` only honoured for `RULE_ADMIN`.  
**Violation Response**: `403 Forbidden` if `RULE_READER` attempts `includeInactive=true`

---

## 13. Cache Consistency Rules

### BR-CC-01: Cache Warm-Up on Startup
**Statement**: On application startup, `InMemoryRuleCache` must be populated with all `ACTIVE` rule versions from the database before the service begins accepting traffic.  
**Check**: Implemented as `@EventListener(ApplicationReadyEvent::class)` in cache adapter.

### BR-CC-02: Cache Update Is Synchronous with Lifecycle Events
**Statement**: The `InMemoryRuleCache` update triggered by `RuleLifecycleEvent` occurs within the same application transaction scope. If the cache update fails (e.g., DMN parse error during warm-up), the exception must be logged and the cache entry skipped — it must not roll back the database transaction.  
**Check**: Event listener uses `@TransactionalEventListener(phase = AFTER_COMMIT)` to decouple from DB transaction.

---

## 14. Audit Logging Rules

### BR-AL-01: Every Lifecycle Transition Must Be Audited
**Statement**: Every state transition listed in the business logic model (Section 5 of business-logic-model.md) must produce a record in `rule_audit_log`.  
**Check**: Written inside `RuleManagementService` after domain method invocation and before event publication.

### BR-AL-02: Audit Log Is Append-Only
**Statement**: Records in `rule_audit_log` must never be updated or deleted through the application.  
**Check**: No update/delete operations on audit log table in any adapter.

### BR-AL-03: Correlation ID and User ID Are Mandatory in Audit
**Statement**: Every audit log entry must contain a non-null `user_id` (from JWT `sub` claim) and `correlation_id` (from MDC).  
**Check**: Commands always carry `userId` and `correlationId`; validated as `@NotBlank` at command construction.

---

## 15. Idempotency and Edge-Case Rules

### BR-IE-01: Activating an Already-Active Version Is a No-Op
**Statement**: If `POST /rules/{id}/versions/{v}/activate` is called and version `v` is already `ACTIVE`, the operation succeeds (returns 200) without creating a duplicate audit entry.  
**Check**: Check version status before applying transition; if already `ACTIVE`, return success without DB write.

### BR-IE-02: Deactivating an Already-Inactive Version Is Rejected
**Statement**: Attempting to deactivate a version that is already `INACTIVE` returns a 422 error (not idempotent for deactivation).  
**Check**: Verify `status != INACTIVE` before transition.  
**Violation Response**: `422 Unprocessable Entity` — `"Version {v} is already INACTIVE."`

### BR-IE-03: Rule Creation Is Not Idempotent
**Statement**: Submitting the same rule name + rule set combination twice always returns `409 Conflict` on the second request (BR-RC-02). There is no client-supplied idempotency key for rule creation.  
**Violation Response**: `409 Conflict`
