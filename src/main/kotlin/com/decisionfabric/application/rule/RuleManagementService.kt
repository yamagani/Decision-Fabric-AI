package com.decisionfabric.application.rule

import com.decisionfabric.application.ports.`in`.RuleManagementUseCase
import com.decisionfabric.application.ports.out.RuleRepositoryPort
import com.decisionfabric.application.ports.out.PagedResult
import com.decisionfabric.application.rule.command.ActivateVersionCommand
import com.decisionfabric.application.rule.command.CreateRuleCommand
import com.decisionfabric.application.rule.command.CreateRuleSetCommand
import com.decisionfabric.application.rule.command.DeactivateVersionCommand
import com.decisionfabric.application.rule.command.DeleteRuleCommand
import com.decisionfabric.application.rule.command.DeleteRuleSetCommand
import com.decisionfabric.application.rule.command.DiscardVersionCommand
import com.decisionfabric.application.rule.command.ImportDmnCommand
import com.decisionfabric.application.rule.command.PurgeVersionCommand
import com.decisionfabric.application.rule.command.UpdateRuleCommand
import com.decisionfabric.application.rule.command.ValidateDmnCommand
import com.decisionfabric.application.rule.query.DmnImportResultView
import com.decisionfabric.application.rule.query.DmnValidationResultView
import com.decisionfabric.application.rule.query.PagedRuleSetView
import com.decisionfabric.application.rule.query.PagedRuleView
import com.decisionfabric.application.rule.query.RuleSetView
import com.decisionfabric.application.rule.query.RuleVersionView
import com.decisionfabric.application.rule.query.RuleView
import com.decisionfabric.domain.rule.DmnXmlContent
import com.decisionfabric.domain.rule.Rule
import com.decisionfabric.domain.rule.RuleId
import com.decisionfabric.domain.rule.RuleSet
import com.decisionfabric.domain.rule.RuleSetId
import com.decisionfabric.domain.rule.RuleSetStatus
import com.decisionfabric.domain.rule.RuleStatus
import com.decisionfabric.domain.rule.RuleVersionStatus
import com.decisionfabric.domain.rule.event.RuleLifecycleEvent
import com.decisionfabric.domain.shared.BusinessRuleViolationException
import com.decisionfabric.domain.shared.ConflictException
import com.decisionfabric.domain.shared.EntityNotFoundException
import com.decisionfabric.domain.shared.ValidationException
import org.slf4j.MDC
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class RuleManagementService(
    private val ruleRepositoryPort: RuleRepositoryPort,
    private val dmnSchemaValidatorPort: com.decisionfabric.application.ports.out.DmnSchemaValidatorPort,
    private val ruleAuditPort: RuleAuditPort,
    private val eventPublisher: ApplicationEventPublisher
) : RuleManagementUseCase {

    // -------------------------------------------------------------------------
    // RuleSet operations
    // -------------------------------------------------------------------------

    @Transactional(isolation = Isolation.SERIALIZABLE)
    override fun createRuleSet(command: CreateRuleSetCommand): RuleSetView {
        return withMdc(command.correlationId) {
            if (ruleRepositoryPort.existsRuleSetByName(command.name.trim())) {
                throw ConflictException("Rule set with name '${command.name}' already exists")
            }
            val ruleSet = RuleSet.create(
                id = RuleSetId.generate(),
                name = command.name,
                description = command.description,
                createdBy = command.userId
            )
            val saved = ruleRepositoryPort.saveRuleSet(ruleSet)
            ruleAuditPort.append(
                RuleAuditRecord(
                    entityId = saved.id.value.toString(),
                    entityType = "RULE_SET",
                    action = RuleAuditAction.CREATE,
                    performedBy = command.userId,
                    correlationId = command.correlationId,
                    detail = "Created rule set '${saved.name}'"
                )
            )
            saved.toView(ruleCount = 0, activeRuleCount = 0)
        }
    }

    @Transactional(readOnly = true)
    override fun getRuleSet(ruleSetId: RuleSetId): RuleSetView {
        val ruleSet = ruleRepositoryPort.findRuleSetById(ruleSetId)
            ?: throw EntityNotFoundException("RuleSet", ruleSetId.value)
        val ruleCount = ruleRepositoryPort.countRulesInSet(ruleSetId)
        val activeCount = ruleRepositoryPort.countActiveRulesInSet(ruleSetId)
        return ruleSet.toView(ruleCount, activeCount)
    }

    @Transactional(readOnly = true)
    override fun listRuleSets(page: Int, size: Int, includeInactive: Boolean): PagedRuleSetView {
        val result: PagedResult<RuleSet> = ruleRepositoryPort.findAllRuleSets(page, size, includeInactive)
        return PagedRuleSetView(
            content = result.content.map { rs ->
                rs.toView(
                    ruleCount = ruleRepositoryPort.countRulesInSet(rs.id),
                    activeRuleCount = ruleRepositoryPort.countActiveRulesInSet(rs.id)
                )
            },
            page = result.page,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    override fun deleteRuleSet(command: DeleteRuleSetCommand) {
        withMdc(command.correlationId) {
            val ruleSet = ruleRepositoryPort.findRuleSetByIdForUpdate(command.ruleSetId)
                ?: throw EntityNotFoundException("RuleSet", command.ruleSetId.value)
            if (ruleRepositoryPort.countActiveRulesInSet(command.ruleSetId) > 0) {
                throw BusinessRuleViolationException(
                    "Cannot delete rule set '${command.ruleSetId.value}' — it still contains active rules"
                )
            }
            val deactivated = ruleSet.deactivate()
            ruleRepositoryPort.saveRuleSet(deactivated)
            ruleAuditPort.append(
                RuleAuditRecord(
                    entityId = command.ruleSetId.value.toString(),
                    entityType = "RULE_SET",
                    action = RuleAuditAction.DELETE,
                    performedBy = command.userId,
                    correlationId = command.correlationId,
                    detail = "Deleted (soft) rule set '${ruleSet.name}'"
                )
            )
        }
    }

    // -------------------------------------------------------------------------
    // Rule operations
    // -------------------------------------------------------------------------

    @Transactional(isolation = Isolation.SERIALIZABLE)
    override fun createRule(command: CreateRuleCommand): RuleView {
        return withMdc(command.correlationId) {
            validateRuleSet(command.ruleSetId)
            if (ruleRepositoryPort.existsRuleByNameInSet(command.ruleSetId, command.name.trim())) {
                throw ConflictException("A rule named '${command.name}' already exists in this rule set")
            }
            val dmnXml = validatedDmnXml(command.dmnXml, command.correlationId)
            val rule = Rule.create(
                id = RuleId.generate(),
                ruleSetId = command.ruleSetId,
                name = command.name,
                description = command.description,
                dmnXml = dmnXml,
                createdBy = command.userId
            )
            val now = Instant.now()
            val saved = ruleRepositoryPort.saveRule(rule)
            ruleAuditPort.append(
                RuleAuditRecord(
                    entityId = saved.id.value.toString(),
                    entityType = "RULE",
                    action = RuleAuditAction.CREATE,
                    performedBy = command.userId,
                    correlationId = command.correlationId,
                    detail = "Created rule '${saved.name}' v1"
                )
            )
            publishRuleCreated(saved, command.userId, command.correlationId)
            saved.toView(ruleSetName = loadRuleSetName(saved.ruleSetId))
        }
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    override fun updateRule(command: UpdateRuleCommand): RuleView {
        return withMdc(command.correlationId) {
            val rule = ruleRepositoryPort.findRuleByIdForUpdate(command.ruleId)
                ?: throw EntityNotFoundException("Rule", command.ruleId.value)
            if (rule.status == RuleStatus.INACTIVE) {
                throw BusinessRuleViolationException("Cannot update an INACTIVE rule '${command.ruleId.value}'")
            }
            command.name?.let { newName ->
                if (newName.trim().lowercase() != rule.name.trim().lowercase()) {
                    if (ruleRepositoryPort.existsRuleByNameInSet(rule.ruleSetId, newName.trim(), excludeId = rule.id)) {
                        throw ConflictException("A rule named '$newName' already exists in this rule set")
                    }
                }
            }
            val dmnXml = validatedDmnXml(command.dmnXml, command.correlationId)
            val updated = rule.addVersion(dmnXml, command.userId).let { withNewVersion ->
                withNewVersion.copy(
                    name = command.name?.trim() ?: withNewVersion.name,
                    description = command.description ?: withNewVersion.description
                )
            }
            val saved = ruleRepositoryPort.saveRule(updated)
            ruleAuditPort.append(
                RuleAuditRecord(
                    entityId = saved.id.value.toString(),
                    entityType = "RULE",
                    action = RuleAuditAction.VERSION_CREATED,
                    performedBy = command.userId,
                    correlationId = command.correlationId,
                    detail = "Added version ${saved.nextVersionNumber() - 1} to rule '${saved.name}'"
                )
            )
            eventPublisher.publishEvent(
                RuleLifecycleEvent.RuleVersionCreated(
                    ruleId = saved.id,
                    newVersion = saved.nextVersionNumber() - 1,
                    userId = command.userId,
                    correlationId = command.correlationId
                )
            )
            saved.toView(ruleSetName = loadRuleSetName(saved.ruleSetId))
        }
    }

    @Transactional(readOnly = true)
    override fun getRule(ruleId: RuleId): RuleView {
        val rule = ruleRepositoryPort.findRuleById(ruleId)
            ?: throw EntityNotFoundException("Rule", ruleId.value)
        return rule.toView(ruleSetName = loadRuleSetName(rule.ruleSetId))
    }

    @Transactional(readOnly = true)
    override fun listRules(
        ruleSetId: RuleSetId?,
        page: Int,
        size: Int,
        search: String?,
        includeInactive: Boolean
    ): PagedRuleView {
        val result = ruleRepositoryPort.findAllRules(ruleSetId, page, size, search, includeInactive)
        val ruleSetNames = mutableMapOf<RuleSetId, String>()
        return PagedRuleView(
            content = result.content.map { rule ->
                rule.toView(
                    ruleSetName = ruleSetNames.getOrPut(rule.ruleSetId) { loadRuleSetName(rule.ruleSetId) }
                )
            },
            page = result.page,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    override fun deleteRule(command: DeleteRuleCommand) {
        withMdc(command.correlationId) {
            val rule = ruleRepositoryPort.findRuleByIdForUpdate(command.ruleId)
                ?: throw EntityNotFoundException("Rule", command.ruleId.value)
            val deleted = rule.softDelete()
            ruleRepositoryPort.saveRule(deleted)
            ruleAuditPort.append(
                RuleAuditRecord(
                    entityId = command.ruleId.value.toString(),
                    entityType = "RULE",
                    action = RuleAuditAction.DELETE,
                    performedBy = command.userId,
                    correlationId = command.correlationId,
                    detail = "Soft-deleted rule '${rule.name}'"
                )
            )
            publishRuleDeleted(deleted, command.userId, command.correlationId)
        }
    }

    // -------------------------------------------------------------------------
    // Version lifecycle
    // -------------------------------------------------------------------------

    @Transactional(isolation = Isolation.SERIALIZABLE)
    override fun activateVersion(command: ActivateVersionCommand): RuleVersionView {
        return withMdc(command.correlationId) {
            val rule = ruleRepositoryPort.findRuleByIdForUpdate(command.ruleId)
                ?: throw EntityNotFoundException("Rule", command.ruleId.value)
            val updated = rule.activateVersion(command.version, command.userId)
            val saved = ruleRepositoryPort.saveRule(updated)
            val version = saved.findVersion(command.version)!!
            ruleAuditPort.append(
                RuleAuditRecord(
                    entityId = command.ruleId.value.toString(),
                    entityType = "RULE_VERSION",
                    action = RuleAuditAction.ACTIVATE,
                    performedBy = command.userId,
                    correlationId = command.correlationId,
                    detail = "Activated version ${command.version} of rule '${rule.name}'"
                )
            )
            publishVersionActivated(saved, command.version, command.userId, command.correlationId)
            version.toView()
        }
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    override fun deactivateVersion(command: DeactivateVersionCommand): RuleVersionView {
        return withMdc(command.correlationId) {
            val rule = ruleRepositoryPort.findRuleByIdForUpdate(command.ruleId)
                ?: throw EntityNotFoundException("Rule", command.ruleId.value)
            val updated = rule.deactivateVersion(command.version)
            val saved = ruleRepositoryPort.saveRule(updated)
            val version = saved.findVersion(command.version)!!
            ruleAuditPort.append(
                RuleAuditRecord(
                    entityId = command.ruleId.value.toString(),
                    entityType = "RULE_VERSION",
                    action = RuleAuditAction.DEACTIVATE,
                    performedBy = command.userId,
                    correlationId = command.correlationId,
                    detail = "Deactivated version ${command.version} of rule '${rule.name}'"
                )
            )
            publishVersionDeactivated(saved, command.version, command.userId, command.correlationId)
            version.toView()
        }
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    override fun discardVersion(command: DiscardVersionCommand): RuleVersionView {
        return withMdc(command.correlationId) {
            val rule = ruleRepositoryPort.findRuleByIdForUpdate(command.ruleId)
                ?: throw EntityNotFoundException("Rule", command.ruleId.value)
            val updated = rule.discardVersion(command.version)
            val saved = ruleRepositoryPort.saveRule(updated)
            val version = saved.findVersion(command.version)!!
            ruleAuditPort.append(
                RuleAuditRecord(
                    entityId = command.ruleId.value.toString(),
                    entityType = "RULE_VERSION",
                    action = RuleAuditAction.VERSION_CREATED,
                    performedBy = command.userId,
                    correlationId = command.correlationId,
                    detail = "Discarded version ${command.version} of rule '${rule.name}'"
                )
            )
            version.toView()
        }
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    override fun purgeVersion(command: PurgeVersionCommand) {
        withMdc(command.correlationId) {
            val rule = ruleRepositoryPort.findRuleByIdForUpdate(command.ruleId)
                ?: throw EntityNotFoundException("Rule", command.ruleId.value)
            val version = rule.findVersion(command.version)
                ?: throw EntityNotFoundException("Version ${command.version}", command.ruleId.value)
            if (version.status != RuleVersionStatus.INACTIVE) {
                throw BusinessRuleViolationException(
                    "Version ${command.version} of rule '${command.ruleId.value}' must be INACTIVE before purging"
                )
            }
            ruleRepositoryPort.deleteRuleVersion(command.ruleId, command.version)
            ruleAuditPort.append(
                RuleAuditRecord(
                    entityId = command.ruleId.value.toString(),
                    entityType = "RULE_VERSION",
                    action = RuleAuditAction.PURGE,
                    performedBy = command.userId,
                    correlationId = command.correlationId,
                    detail = "Purged version ${command.version} of rule '${rule.name}'"
                )
            )
        }
    }

    // -------------------------------------------------------------------------
    // DMN import / export / validate
    // -------------------------------------------------------------------------

    @Transactional(isolation = Isolation.SERIALIZABLE)
    override fun importDmn(command: ImportDmnCommand): DmnImportResultView {
        return withMdc(command.correlationId) {
            validateRuleSet(command.ruleSetId)
            if (ruleRepositoryPort.existsRuleByNameInSet(command.ruleSetId, command.ruleName.trim())) {
                throw ConflictException("A rule named '${command.ruleName}' already exists in this rule set")
            }
            val dmnXml = validatedDmnXml(command.dmnFileContent, command.correlationId)
            val rule = Rule.create(
                id = RuleId.generate(),
                ruleSetId = command.ruleSetId,
                name = command.ruleName,
                description = command.description,
                dmnXml = dmnXml,
                createdBy = command.userId
            )
            val saved = ruleRepositoryPort.saveRule(rule)
            ruleAuditPort.append(
                RuleAuditRecord(
                    entityId = saved.id.value.toString(),
                    entityType = "RULE",
                    action = RuleAuditAction.IMPORT,
                    performedBy = command.userId,
                    correlationId = command.correlationId,
                    detail = "Imported DMN from '${command.originalFileName}' as rule '${saved.name}'"
                )
            )
            eventPublisher.publishEvent(
                RuleLifecycleEvent.RuleImported(
                    ruleId = saved.id,
                    ruleSetId = saved.ruleSetId,
                    name = saved.name,
                    fileName = command.originalFileName,
                    userId = command.userId,
                    correlationId = command.correlationId
                )
            )
            val version = saved.latestVersion()!!
            DmnImportResultView(
                ruleId = saved.id,
                ruleSetId = saved.ruleSetId,
                name = saved.name,
                version = version.version,
                status = version.status
            )
        }
    }

    @Transactional(readOnly = true)
    override fun exportDmn(ruleId: RuleId, version: Int?): String {
        val rule = ruleRepositoryPort.findRuleById(ruleId)
            ?: throw EntityNotFoundException("Rule", ruleId.value)
        val ruleVersion = if (version != null) {
            rule.findVersion(version)
                ?: throw EntityNotFoundException("Version $version", ruleId.value)
        } else {
            rule.latestVersion()
                ?: throw BusinessRuleViolationException("Rule '${ruleId.value}' has no versions")
        }
        return ruleVersion.dmnXml.value
    }

    @Transactional(readOnly = true)
    override fun validateDmn(command: ValidateDmnCommand): DmnValidationResultView {
        // Size guard: 1 MB max for validation
        if (command.dmnXml.toByteArray(Charsets.UTF_8).size > 1_048_576) {
            throw ValidationException("DMN XML exceeds 1 MB size limit")
        }
        val result = dmnSchemaValidatorPort.validate(command.dmnXml)
        return DmnValidationResultView(valid = result.isValid, errors = result.errors)
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun validateRuleSet(ruleSetId: RuleSetId) {
        val ruleSet = ruleRepositoryPort.findRuleSetById(ruleSetId)
            ?: throw EntityNotFoundException("RuleSet", ruleSetId.value)
        if (ruleSet.status != RuleSetStatus.ACTIVE) {
            throw BusinessRuleViolationException("Rule set '${ruleSetId.value}' is not ACTIVE")
        }
    }

    private fun validatedDmnXml(rawXml: String, correlationId: String): DmnXmlContent {
        if (rawXml.toByteArray(Charsets.UTF_8).size > 1_048_576) {
            throw ValidationException("DMN XML exceeds 1 MB size limit")
        }
        val result = dmnSchemaValidatorPort.validate(rawXml)
        if (!result.isValid) {
            throw com.decisionfabric.domain.shared.DmnValidationException(
                "DMN validation failed: ${result.errors.joinToString("; ")}"
            )
        }
        return DmnXmlContent(rawXml)
    }

    private fun publishEvents(rule: Rule) {
        // Rule is a data class — AggregateRoot domain events are reset on each copy().
        // Events are therefore published directly from the service based on observed state changes.
        // No-op: callers publish explicit events via publishEvent() where needed.
    }

    private fun publishVersionActivated(rule: Rule, version: Int, userId: String, correlationId: String) {
        eventPublisher.publishEvent(
            RuleLifecycleEvent.RuleVersionActivated(
                ruleId = rule.id, version = version, userId = userId, correlationId = correlationId
            )
        )
    }

    private fun publishVersionDeactivated(rule: Rule, version: Int, userId: String, correlationId: String) {
        eventPublisher.publishEvent(
            RuleLifecycleEvent.RuleVersionDeactivated(
                ruleId = rule.id, version = version, userId = userId, correlationId = correlationId
            )
        )
    }

    private fun publishRuleDeleted(rule: Rule, userId: String, correlationId: String) {
        eventPublisher.publishEvent(
            RuleLifecycleEvent.RuleDeleted(
                ruleId = rule.id,
                cascadedVersions = rule.versions.map { it.version },
                userId = userId,
                correlationId = correlationId
            )
        )
    }

    private fun publishRuleCreated(rule: Rule, userId: String, correlationId: String) {
        eventPublisher.publishEvent(
            RuleLifecycleEvent.RuleCreated(
                ruleId = rule.id,
                ruleSetId = rule.ruleSetId,
                name = rule.name,
                initialVersion = 1,
                userId = userId,
                correlationId = correlationId
            )
        )
    }

    private fun loadRuleSetName(ruleSetId: RuleSetId): String =
        ruleRepositoryPort.findRuleSetById(ruleSetId)?.name ?: ruleSetId.value.toString()

    private inline fun <T> withMdc(correlationId: String, block: () -> T): T {
        MDC.put("correlationId", correlationId)
        return try {
            block()
        } finally {
            MDC.remove("correlationId")
        }
    }
}

// -------------------------------------------------------------------------
// Mapping extensions (private to this package)
// -------------------------------------------------------------------------

internal fun RuleSet.toView(ruleCount: Int, activeRuleCount: Int) = RuleSetView(
    id = id,
    name = name,
    description = description,
    status = status,
    ruleCount = ruleCount,
    activeRuleCount = activeRuleCount,
    createdBy = createdBy,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun Rule.toView(ruleSetName: String) = RuleView(
    id = id,
    ruleSetId = ruleSetId,
    ruleSetName = ruleSetName,
    name = name,
    description = description,
    status = status,
    versions = versions.map { it.toView() },
    latestVersion = latestVersion()?.version ?: 0,
    activeVersionCount = activeVersions().size,
    createdBy = createdBy,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun com.decisionfabric.domain.rule.RuleVersion.toView() = RuleVersionView(
    version = version,
    status = status,
    createdBy = createdBy,
    createdAt = createdAt,
    activatedAt = activatedAt,
    activatedBy = activatedBy
)
