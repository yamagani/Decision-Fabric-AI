# Component Methods — Decision-Fabric-AI

> **Note**: Method signatures define interfaces. Detailed business logic / FEEL expression evaluation rules are defined in Functional Design (CONSTRUCTION phase).

---

## Domain: RuleAggregate

```kotlin
// com.decisionfabric.domain.rule.Rule
class Rule {
    fun activate(): Rule
    fun deactivate(): Rule
    fun delete(): Rule
    fun withUpdatedContent(dmnXml: DmnXmlContent, description: String): Rule  // returns new Rule with incremented version
    fun isEligibleForDeletion(): Boolean
    fun toDmnXmlContent(): DmnXmlContent
}
```

---

## Domain: RuleSetAggregate

```kotlin
// com.decisionfabric.domain.ruleset.RuleSet
class RuleSet {
    fun enable(): RuleSet
    fun disable(): RuleSet
    fun addRule(ref: RuleReference): RuleSet
    fun removeRule(ruleId: RuleId): RuleSet
    fun reorder(orderedRuleIds: List<RuleId>): RuleSet
}
```

---

## Application: DecisionEvaluationService

```kotlin
// com.decisionfabric.application.decision.DecisionEvaluationService
interface DecisionEvaluationUseCase {
    fun evaluate(request: DecisionRequest): DecisionResult
}

class DecisionEvaluationService : DecisionEvaluationUseCase {
    fun evaluate(request: DecisionRequest): DecisionResult
    private fun loadActiveRules(): List<Rule>
    private fun shouldAugmentWithAi(result: DmnEvaluationResult, threshold: Double): Boolean
    private fun buildFallbackResult(request: DecisionRequest, reason: String): DecisionResult
}
```

---

## Application: RuleManagementService

```kotlin
// com.decisionfabric.application.rule.RuleManagementService
interface RuleManagementUseCase {
    fun createRule(command: CreateRuleCommand): Rule
    fun updateRule(command: UpdateRuleCommand): Rule
    fun activateRule(ruleId: RuleId): Rule
    fun deactivateRule(ruleId: RuleId): Rule
    fun deleteRule(ruleId: RuleId)
    fun getRule(ruleId: RuleId): Rule
    fun listRules(filter: RuleFilter): Page<Rule>
    fun importDmnModel(dmnXml: DmnXmlContent): List<Rule>          // atomic import
    fun exportDmnModel(ruleIds: List<RuleId>): DmnXmlContent
    fun createRuleSet(command: CreateRuleSetCommand): RuleSet
    fun updateRuleSet(command: UpdateRuleSetCommand): RuleSet
    fun enableRuleSet(ruleSetId: RuleSetId): RuleSet
    fun disableRuleSet(ruleSetId: RuleSetId): RuleSet
    fun reorderRuleSet(ruleSetId: RuleSetId, orderedRuleIds: List<RuleId>): RuleSet
    fun deleteRuleSet(ruleSetId: RuleSetId)
}
```

---

## Application: AuditQueryService

```kotlin
// com.decisionfabric.application.audit.AuditQueryService
interface AuditQueryUseCase {
    fun getDecision(requestId: RequestId): DecisionResult
    fun queryDecisions(query: AuditQuery): Page<DecisionResult>
}

// AuditQuery value object
data class AuditQuery(
    val from: Instant,
    val to: Instant,
    val aiAugmented: Boolean? = null,
    val pageToken: String? = null,
    val limit: Int = 50
)
```

---

## Application: SystemConfigService

```kotlin
// com.decisionfabric.application.config.SystemConfigService
interface SystemConfigUseCase {
    fun getAiProviderConfig(): AiProviderConfig
    fun updateAiProviderConfig(command: UpdateAiProviderConfigCommand): AiProviderConfig
    fun updateAiProviderCredentials(command: UpdateAiProviderCredentialsCommand)
}
```

---

## Port: DmnEnginePort

```kotlin
// com.decisionfabric.application.ports.out.DmnEnginePort
interface DmnEnginePort {
    fun evaluate(dmnXml: DmnXmlContent, inputData: Map<String, Any>): DmnEvaluationResult
    fun validateSchema(dmnXml: DmnXmlContent): DmnValidationResult
    fun getEngineInfo(): DmnEngineInfo   // name, version, compliance level
}

data class DmnEvaluationResult(
    val outputs: Map<String, Any>,
    val matchedRules: List<MatchedDmnRule>,
    val hitPolicy: String,
    val confidenceScore: Double
)

data class DmnValidationResult(
    val valid: Boolean,
    val errors: List<String>
)
```

---

## Port: RuleRepositoryPort

```kotlin
// com.decisionfabric.application.ports.out.RuleRepositoryPort
interface RuleRepositoryPort {
    fun save(rule: Rule): Rule
    fun findById(ruleId: RuleId): Rule?
    fun findAll(filter: RuleFilter): Page<Rule>
    fun findAllActive(): List<Rule>
    fun delete(ruleId: RuleId)
    fun saveRuleSet(ruleSet: RuleSet): RuleSet
    fun findRuleSetById(ruleSetId: RuleSetId): RuleSet?
    fun findAllEnabledRuleSets(): List<RuleSet>
    fun deleteRuleSet(ruleSetId: RuleSetId)
}
```

---

## Port: AuditRepositoryPort

```kotlin
// com.decisionfabric.application.ports.out.AuditRepositoryPort
interface AuditRepositoryPort {
    fun append(result: DecisionResult)
    fun findByRequestId(requestId: RequestId): DecisionResult?
    fun query(query: AuditQuery): Page<DecisionResult>
}
```

---

## Port: LlmProviderPort

```kotlin
// com.decisionfabric.application.ports.out.LlmProviderPort
interface LlmProviderPort {
    fun augmentDecision(request: AiAugmentationRequest): AiAugmentationResult
    fun isAvailable(): Boolean
}

data class AiAugmentationRequest(
    val decisionRequest: DecisionRequest,
    val dmnContext: DmnEvaluationResult,
    val modelId: String
)

data class AiAugmentationResult(
    val outcome: String,
    val reasoning: String,
    val confidenceScore: Double,
    val modelUsed: String
)
```

---

## Port: AiProviderConfigPort

```kotlin
// com.decisionfabric.application.ports.out.AiProviderConfigPort
interface AiProviderConfigPort {
    fun getConfig(): AiProviderConfig
    fun saveConfig(config: AiProviderConfig)
    fun saveCredentials(credentials: AiProviderCredentials)
}

data class AiProviderConfig(
    val providerEndpoint: String,
    val modelId: String,
    val confidenceThreshold: Double,        // 0.0–1.0
    val timeoutMs: Long,
    val circuitBreakerEnabled: Boolean
)
```

---

## Inbound: DecisionApiController

```kotlin
// com.decisionfabric.adapter.inbound.rest.decision.DecisionApiController
@RestController
class DecisionApiController {
    @PostMapping("/decisions/evaluate")
    fun evaluateDecision(@RequestBody @Valid request: DecisionRequestDto,
                         authentication: Authentication): ResponseEntity<DecisionResultDto>

    @GetMapping("/decisions/{requestId}")
    fun getDecision(@PathVariable requestId: String,
                    authentication: Authentication): ResponseEntity<DecisionResultDto>

    @GetMapping("/decisions")
    fun queryDecisions(@Valid query: AuditQueryParams,
                       authentication: Authentication): ResponseEntity<PagedDecisionResultDto>
}
```

---

## Inbound: RuleApiController

```kotlin
// com.decisionfabric.adapter.inbound.rest.rule.RuleApiController
@RestController
class RuleApiController {
    @PostMapping("/rules")
    fun createRule(@RequestBody @Valid dto: CreateRuleDto, auth: Authentication): ResponseEntity<RuleDto>

    @PutMapping("/rules/{id}")
    fun updateRule(@PathVariable id: String, @RequestBody @Valid dto: UpdateRuleDto, auth: Authentication): ResponseEntity<RuleDto>

    @PostMapping("/rules/{id}/activate")
    fun activateRule(@PathVariable id: String, auth: Authentication): ResponseEntity<RuleDto>

    @PostMapping("/rules/{id}/deactivate")
    fun deactivateRule(@PathVariable id: String, auth: Authentication): ResponseEntity<RuleDto>

    @DeleteMapping("/rules/{id}")
    fun deleteRule(@PathVariable id: String, auth: Authentication): ResponseEntity<Void>

    @GetMapping("/rules/{id}")
    fun getRule(@PathVariable id: String, auth: Authentication): ResponseEntity<RuleDto>

    @GetMapping("/rules")
    fun listRules(@Valid filter: RuleFilterParams, auth: Authentication): ResponseEntity<PagedRuleDto>

    @PostMapping("/rules/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun importDmn(@RequestParam file: MultipartFile, auth: Authentication): ResponseEntity<ImportResultDto>

    @GetMapping("/rules/export")
    fun exportDmn(@RequestParam ids: List<String>, auth: Authentication): ResponseEntity<ByteArray>

    // Rule Set endpoints
    @PostMapping("/rule-sets")
    fun createRuleSet(@RequestBody @Valid dto: CreateRuleSetDto, auth: Authentication): ResponseEntity<RuleSetDto>

    @PostMapping("/rule-sets/{id}/enable")
    fun enableRuleSet(@PathVariable id: String, auth: Authentication): ResponseEntity<RuleSetDto>

    @PostMapping("/rule-sets/{id}/disable")
    fun disableRuleSet(@PathVariable id: String, auth: Authentication): ResponseEntity<RuleSetDto>

    @PutMapping("/rule-sets/{id}/order")
    fun reorderRuleSet(@PathVariable id: String, @RequestBody orderedIds: List<String>, auth: Authentication): ResponseEntity<RuleSetDto>

    @DeleteMapping("/rule-sets/{id}")
    fun deleteRuleSet(@PathVariable id: String, auth: Authentication): ResponseEntity<Void>
}
```

---

## Inbound: ConfigApiController

```kotlin
// com.decisionfabric.adapter.inbound.rest.config.ConfigApiController
@RestController
class ConfigApiController {
    @PutMapping("/config/ai-provider")
    fun updateAiProviderConfig(@RequestBody @Valid dto: AiProviderConfigDto,
                               auth: Authentication): ResponseEntity<AiProviderConfigDto>

    @PutMapping("/config/ai-provider/credentials")
    fun updateAiProviderCredentials(@RequestBody @Valid dto: AiProviderCredentialsDto,
                                    auth: Authentication): ResponseEntity<Void>
}
```

---

## Outbound: PluggableDmnEngineAdapter

```kotlin
// com.decisionfabric.adapter.outbound.dmn.PluggableDmnEngineAdapter
class CamundaDmnEngineAdapter : DmnEnginePort {
    override fun evaluate(dmnXml: DmnXmlContent, inputData: Map<String, Any>): DmnEvaluationResult
    override fun validateSchema(dmnXml: DmnXmlContent): DmnValidationResult
    override fun getEngineInfo(): DmnEngineInfo
}

class DroolsDmnEngineAdapter : DmnEnginePort {
    override fun evaluate(dmnXml: DmnXmlContent, inputData: Map<String, Any>): DmnEvaluationResult
    override fun validateSchema(dmnXml: DmnXmlContent): DmnValidationResult
    override fun getEngineInfo(): DmnEngineInfo
}
```

---

## Outbound: LlmProviderAdapter

```kotlin
// com.decisionfabric.adapter.outbound.ai.LlmProviderAdapter
class LlmProviderAdapter : LlmProviderPort {
    // Resilience4j @CircuitBreaker applied
    override fun augmentDecision(request: AiAugmentationRequest): AiAugmentationResult
    override fun isAvailable(): Boolean
    private fun buildPrompt(request: AiAugmentationRequest): String
    private fun parseResponse(rawResponse: String): AiAugmentationResult
}
```

---

## Outbound: InMemoryRuleCache

```kotlin
// com.decisionfabric.adapter.outbound.cache.InMemoryRuleCache
class InMemoryRuleCache {
    fun getActiveRules(): List<Rule>
    fun invalidateAndReload()                       // called on rule lifecycle events
    @EventListener fun onRuleLifecycleEvent(event: RuleLifecycleEvent)
}
```
