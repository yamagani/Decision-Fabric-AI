# Domain Entities — Unit 2: Rule Management

## Package Root
`com.decisionfabric.domain.rule`

---

## 1. Aggregate Roots

### 1.1 `RuleSet`

**Type**: Aggregate Root  
**Package**: `com.decisionfabric.domain.rule`  
**Invariants**:
- Name must be globally unique (enforced at application service layer via repository check before persist)
- Cannot be soft-deleted while any contained rule has at least one `ACTIVE` version

```kotlin
data class RuleSet(
    val id: RuleSetId,
    val name: String,                         // globally unique
    val description: String,
    val status: RuleSetStatus,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant
) : AggregateRoot<RuleSetId>()
```

**Methods** (domain behaviour encapsulated on the aggregate):

| Method | Signature | Guard | Produces |
|---|---|---|---|
| `deactivate` | `fun deactivate(): RuleSet` | Must be in `ACTIVE` state | New `RuleSet` with `status=INACTIVE` |

**Status Enum**:
```kotlin
enum class RuleSetStatus { ACTIVE, INACTIVE }
```

---

### 1.2 `Rule`

**Type**: Aggregate Root  
**Package**: `com.decisionfabric.domain.rule`  
**Invariants**:
- Must belong to a `RuleSet` (`ruleSetId` is always set)
- `name` is unique within its `ruleSetId` (composite uniqueness enforced at application service layer)
- An `ACTIVE` version's `dmnXml` is immutable — no update allowed once `status=ACTIVE`
- Multiple `RuleVersion` entities may concurrently be in `ACTIVE` state

```kotlin
data class Rule(
    val id: RuleId,
    val ruleSetId: RuleSetId,
    val name: String,                         // unique within ruleSetId
    val description: String,
    val status: RuleStatus,
    val versions: List<RuleVersion>,          // ordered by version ASC
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant
) : AggregateRoot<RuleId>()
```

**Methods**:

| Method | Signature | Guard | Produces |
|---|---|---|---|
| `addVersion` | `fun addVersion(dmnXml: DmnXmlContent, createdBy: String): Rule` | Rule must be `ACTIVE`; `dmnXml` must be pre-validated | New `Rule` with appended `RuleVersion(status=DRAFT, version=nextVersion)` |
| `activateVersion` | `fun activateVersion(version: Int, activatedBy: String): Rule` | Version must exist and be `DRAFT`; Rule must be `ACTIVE` | New `Rule` with that version transitioned to `ACTIVE` |
| `deactivateVersion` | `fun deactivateVersion(version: Int): Rule` | Version must exist and be `ACTIVE` | New `Rule` with that version transitioned to `INACTIVE` |
| `discardVersion` | `fun discardVersion(version: Int): Rule` | Version must exist and be `DRAFT` | New `Rule` with that version transitioned to `INACTIVE` |
| `softDelete` | `fun softDelete(): Rule` | No guard (can soft-delete regardless of version states) | New `Rule` with `status=INACTIVE`; all `ACTIVE` versions set to `INACTIVE` |
| `activeVersions` | `fun activeVersions(): List<RuleVersion>` | — | Filtered list of versions with `status=ACTIVE` |
| `latestVersion` | `fun latestVersion(): RuleVersion?` | — | Version with highest version number, or null |
| `nextVersionNumber` | `fun nextVersionNumber(): Int` | — | `max(versions.map { it.version }) + 1`, or `1` if empty |

**Status Enum**:
```kotlin
enum class RuleStatus { ACTIVE, INACTIVE }
```

---

## 2. Entities (within `Rule` aggregate)

### 2.1 `RuleVersion`

**Type**: Entity (child of `Rule` aggregate root)  
**Package**: `com.decisionfabric.domain.rule`  
**Lifecycle**: Owned and mutated exclusively through the `Rule` aggregate root methods

```kotlin
data class RuleVersion(
    val ruleId: RuleId,
    val version: Int,                          // monotonically increasing, starts at 1
    val dmnXml: DmnXmlContent,                 // immutable once status=ACTIVE
    val status: RuleVersionStatus,
    val createdBy: String,
    val createdAt: Instant,
    val activatedAt: Instant?,                 // null until activated
    val activatedBy: String?                   // null until activated
)
```

**Status Enum and Valid Transitions**:

```kotlin
enum class RuleVersionStatus { DRAFT, ACTIVE, INACTIVE }
```

| From | To | Method on `Rule` | Condition |
|---|---|---|---|
| `DRAFT` | `ACTIVE` | `activateVersion()` | DMN schema pre-validated |
| `DRAFT` | `INACTIVE` | `discardVersion()` | — |
| `ACTIVE` | `INACTIVE` | `deactivateVersion()` | — |
| `ACTIVE` | `DRAFT` | *(forbidden)* | Immutability invariant |
| `INACTIVE` | *(any)* | *(forbidden)* | Terminal state |

---

## 3. Value Objects

### 3.1 `RuleId`

```kotlin
@JvmInline
value class RuleId(val value: UUID) {
    companion object {
        fun generate(): RuleId = RuleId(UUID.randomUUID())
        fun from(value: String): RuleId = RuleId(UUID.fromString(value))
    }
}
```

### 3.2 `RuleSetId`

```kotlin
@JvmInline
value class RuleSetId(val value: UUID) {
    companion object {
        fun generate(): RuleSetId = RuleSetId(UUID.randomUUID())
        fun from(value: String): RuleSetId = RuleSetId(UUID.fromString(value))
    }
}
```

### 3.3 `DmnXmlContent`

**Invariants**:
- Must be a non-blank UTF-8 string
- Must be a well-formed XML document (validated at construction)
- Structural DMN 1.4 schema validation is performed separately by `DmnSchemaValidatorPort` before this value object is created

```kotlin
@JvmInline
value class DmnXmlContent(val value: String) {
    init {
        require(value.isNotBlank()) { "DMN XML content must not be blank" }
        require(value.trimStart().startsWith("<")) { "DMN XML content must begin with an XML element" }
    }
}
```

### 3.4 `RuleReference`

A lightweight read-model projection of a `Rule` for embedding in `RuleSet` responses and cache keys. Not persisted as a separate entity; derived from the `Rule` table.

```kotlin
data class RuleReference(
    val ruleId: RuleId,
    val name: String,
    val status: RuleStatus,
    val activeVersionCount: Int
)
```

---

## 4. Domain Events

### 4.1 `RuleLifecycleEvent`

**Package**: `com.decisionfabric.domain.rule.event`

```kotlin
sealed class RuleLifecycleEvent : DomainEvent() {

    data class RuleCreated(
        override val occurredOn: Instant = Instant.now(),
        val ruleId: RuleId,
        val ruleSetId: RuleSetId,
        val name: String,
        val initialVersion: Int,
        val userId: String,
        val correlationId: String
    ) : RuleLifecycleEvent()

    data class RuleVersionCreated(
        override val occurredOn: Instant = Instant.now(),
        val ruleId: RuleId,
        val newVersion: Int,
        val userId: String,
        val correlationId: String
    ) : RuleLifecycleEvent()

    data class RuleVersionActivated(
        override val occurredOn: Instant = Instant.now(),
        val ruleId: RuleId,
        val version: Int,
        val userId: String,
        val correlationId: String
    ) : RuleLifecycleEvent()

    data class RuleVersionDeactivated(
        override val occurredOn: Instant = Instant.now(),
        val ruleId: RuleId,
        val version: Int,
        val userId: String,
        val correlationId: String
    ) : RuleLifecycleEvent()

    data class RuleDeleted(
        override val occurredOn: Instant = Instant.now(),
        val ruleId: RuleId,
        val cascadedVersions: List<Int>,      // ACTIVE versions transitioned to INACTIVE
        val userId: String,
        val correlationId: String
    ) : RuleLifecycleEvent()

    data class RuleImported(
        override val occurredOn: Instant = Instant.now(),
        val ruleId: RuleId,
        val ruleSetId: RuleSetId,
        val name: String,
        val fileName: String,
        val userId: String,
        val correlationId: String
    ) : RuleLifecycleEvent()
}
```

---

## 5. Application Layer Commands

**Package**: `com.decisionfabric.application.rule.command`

These commands are the input contracts for `RuleManagementUseCase`. They are value objects with no domain logic.

```kotlin
data class CreateRuleCommand(
    val ruleSetId: RuleSetId,
    val name: String,
    val description: String,
    val dmnXml: String,          // raw XML string; validated by use-case
    val userId: String,
    val correlationId: String
)

data class UpdateRuleCommand(
    val ruleId: RuleId,
    val name: String?,           // null = no change to name
    val description: String?,    // null = no change to description
    val dmnXml: String,          // new DMN XML; required (branch model always creates version)
    val userId: String,
    val correlationId: String
)

data class ActivateVersionCommand(
    val ruleId: RuleId,
    val version: Int,
    val userId: String,
    val correlationId: String
)

data class DeactivateVersionCommand(
    val ruleId: RuleId,
    val version: Int,
    val userId: String,
    val correlationId: String
)

data class DiscardVersionCommand(
    val ruleId: RuleId,
    val version: Int,
    val userId: String,
    val correlationId: String
)

data class DeleteRuleCommand(
    val ruleId: RuleId,
    val userId: String,
    val correlationId: String
)

data class ImportDmnCommand(
    val ruleSetId: RuleSetId,
    val ruleName: String,
    val description: String,
    val dmnFileContent: String,  // raw file bytes as UTF-8 string
    val originalFileName: String,
    val userId: String,
    val correlationId: String
)

data class CreateRuleSetCommand(
    val name: String,
    val description: String,
    val userId: String,
    val correlationId: String
)

data class DeleteRuleSetCommand(
    val ruleSetId: RuleSetId,
    val userId: String,
    val correlationId: String
)

data class ValidateDmnCommand(
    val dmnXml: String,
    val correlationId: String
)
```

---

## 6. Application Layer Query Results

**Package**: `com.decisionfabric.application.rule.query`

Read-model projections returned from `RuleManagementUseCase` query operations.

```kotlin
data class RuleSetView(
    val id: RuleSetId,
    val name: String,
    val description: String,
    val status: RuleSetStatus,
    val ruleCount: Int,
    val activeRuleCount: Int,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class RuleView(
    val id: RuleId,
    val ruleSetId: RuleSetId,
    val ruleSetName: String,
    val name: String,
    val description: String,
    val status: RuleStatus,
    val versions: List<RuleVersionView>,
    val latestVersion: Int,
    val activeVersionCount: Int,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class RuleVersionView(
    val version: Int,
    val status: RuleVersionStatus,
    val createdBy: String,
    val createdAt: Instant,
    val activatedAt: Instant?,
    val activatedBy: String?
    // dmnXml is NOT included in general list responses; exposed only via export endpoint
)

data class DmnImportResultView(
    val ruleId: RuleId,
    val ruleSetId: RuleSetId,
    val name: String,
    val version: Int,
    val status: RuleVersionStatus
)

data class DmnValidationResultView(
    val valid: Boolean,
    val errors: List<String>
)
```

---

## 7. Database Schema Mapping

### `rule_sets` table → `RuleSet`
| Column | Type | Notes |
|---|---|---|
| `id` | `UUID PK` | |
| `name` | `VARCHAR(255) UNIQUE` | Global uniqueness enforced at DB level |
| `description` | `TEXT` | |
| `status` | `VARCHAR(20)` | `ACTIVE` / `INACTIVE` |
| `created_by` | `VARCHAR(255)` | userId from JWT |
| `created_at` | `TIMESTAMPTZ` | |
| `updated_at` | `TIMESTAMPTZ` | |

### `rules` table → `Rule`
| Column | Type | Notes |
|---|---|---|
| `id` | `UUID PK` | |
| `rule_set_id` | `UUID FK → rule_sets.id` | NOT NULL |
| `name` | `VARCHAR(255)` | Unique per `rule_set_id` (composite unique index) |
| `description` | `TEXT` | |
| `status` | `VARCHAR(20)` | `ACTIVE` / `INACTIVE` |
| `created_by` | `VARCHAR(255)` | |
| `created_at` | `TIMESTAMPTZ` | |
| `updated_at` | `TIMESTAMPTZ` | |

### `rule_versions` table → `RuleVersion`
| Column | Type | Notes |
|---|---|---|
| `rule_id` | `UUID FK → rules.id` | |
| `version` | `INT` | Composite PK with `rule_id` |
| `dmn_xml` | `TEXT` | Full DMN XML content |
| `status` | `VARCHAR(20)` | `DRAFT` / `ACTIVE` / `INACTIVE` |
| `created_by` | `VARCHAR(255)` | |
| `created_at` | `TIMESTAMPTZ` | |
| `activated_at` | `TIMESTAMPTZ` | Nullable |
| `activated_by` | `VARCHAR(255)` | Nullable |

### `rule_audit_log` table
| Column | Type | Notes |
|---|---|---|
| `id` | `UUID PK` | |
| `rule_id` | `UUID` | Not FK; preserved for deleted rules |
| `rule_set_id` | `UUID` | Not FK |
| `version` | `INT` | Nullable |
| `action` | `VARCHAR(50)` | Enum-string |
| `user_id` | `VARCHAR(255)` | |
| `correlation_id` | `VARCHAR(255)` | |
| `occurred_at` | `TIMESTAMPTZ` | |
| `metadata` | `JSONB` | File name for imports, etc. |
