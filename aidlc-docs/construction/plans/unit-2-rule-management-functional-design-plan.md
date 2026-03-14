# Functional Design Plan — Unit 2: Rule Management

## Unit Context
**Unit**: Unit 2 — Rule Management
**Stories**: US-1.1 (Create/Update DMN Rule), US-1.2 (Activate Rule Version), US-1.3 (Import/Export DMN File)
**FR Coverage**: FR-01 (Rule CRUD), FR-02 (Rule Versioning), FR-09 (DMN Compliance)
**Domain entities**: `Rule`, `RuleSet`, `RuleId`, `RuleVersion`, `RuleStatus`, `DmnXmlContent`, `RuleLifecycleEvent`

---

## Execution Checklist

- [x] Step 1: Analyze unit context (unit-of-work.md + unit-of-work-story-map.md reviewed)
- [x] Step 2: Create functional design plan (this document)
- [x] Step 3: Answer clarification questions (Section A)
- [x] Step 4: Generate business-logic-model.md
- [x] Step 5: Generate domain-entities.md
- [x] Step 6: Generate business-rules.md
- [x] Step 7: Validate functional design completeness

---

## Section A: Functional Design Questions

---

### Question 1
**Rule name uniqueness** — Rule names must be unique:

A) **Globally** — across the entire system; two rules cannot share the same name regardless of structure
B) **Per rule set** — unique within a rule set; same name allowed in different rule sets
C) **No uniqueness constraint** — names are informational only; ID is the unique key

[Answer]: B

---

### Question 2
**Versioning model** — How are new rule versions created?

A) **Explicit versioning** — a new version is only created when the user explicitly requests it (e.g., `POST /rules/{id}/versions`); regular `PUT /rules/{id}` updates the DRAFT of the current version in-place
B) **Automatic versioning** — every `PUT /rules/{id}` that changes DMN XML automatically increments the version number and creates a new version record; the previous version remains queryable
C) **Branch model** — edits always create a new DRAFT version; the user then activates a specific version when ready

[Answer]: c

---

### Question 3
**Active version constraint** — When a version is activated:

A) **One active per rule** — activating version N automatically deactivates the previously active version; only one version can be ACTIVE at a time per rule
B) **Multiple active versions** — multiple versions of the same rule can be simultaneously ACTIVE (e.g., for A/B rollout)
C) **Active + staging** — only one ACTIVE version; one STAGING version allowed concurrently for preview evaluation

[Answer]: B

---

### Question 4
**Rule sets** — Are rule sets required containers for rules?

A) **Optional grouping** — rules can exist standalone or be grouped into a rule set; rule sets are a convenience for bulk management
B) **Required container** — every rule must belong to a rule set; a rule cannot exist without a rule set
C) **Not in scope for Unit 2** — rule sets are a future enhancement; skip for now

[Answer]:B

---

### Question 5
**DMN import — decision granularity** — When a `.dmn` file containing multiple Decision nodes is imported:

A) **One rule per decision** — each `<decision>` element in the XML becomes a separate `Rule` record; the import returns a list of created rules
B) **One rule for the whole file** — the entire `.dmn` file is stored as a single `Rule` record; the rule name is taken from the root namespace or a parameter supplied by the caller
C) **User choice** — the import API accepts a parameter `granularity=per-decision|whole-file`

[Answer]: B

---

### Question 6
**Immutability of activated versions** — Once a rule version is activated:

A) **Immutable** — an activated version's DMN XML cannot be modified; to change logic the user must create a new version and activate it
B) **Mutable with audit** — an activated version's XML can be edited; the change is logged but no new version is created
C) **Force-update allowed** — admin role can overwrite an active version's XML without creating a new version

[Answer]: A

---

### Question 7
**Deactivation / deletion** — When a rule is deleted or deactivated:

A) **Soft delete only** — status changes to `INACTIVE`; the rule record and all versions remain in the database and are returnable in history queries
B) **Hard delete** — the rule and all its versions are permanently removed from the database
C) **Tombstone** — rule status set to `DELETED`; rule is excluded from all normal queries but preserved for audit trail

[Answer]: A

---

*Once all `[Answer]:` tags are filled, notify me and I will generate the three functional design artifacts (business-logic-model.md, domain-entities.md, business-rules.md).*
