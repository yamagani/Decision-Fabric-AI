package com.decisionfabric.domain.rule

import com.decisionfabric.domain.shared.AggregateRoot
import com.decisionfabric.domain.shared.BusinessRuleViolationException
import com.decisionfabric.domain.shared.EntityNotFoundException
import com.decisionfabric.domain.shared.ValidationException
import java.time.Instant

data class Rule(
    override val id: RuleId,
    val ruleSetId: RuleSetId,
    val name: String,
    val description: String,
    val status: RuleStatus,
    val versions: List<RuleVersion>,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant
) : AggregateRoot<RuleId>() {

    fun nextVersionNumber(): Int =
        if (versions.isEmpty()) 1 else versions.maxOf { it.version } + 1

    fun latestVersion(): RuleVersion? = versions.maxByOrNull { it.version }

    fun activeVersions(): List<RuleVersion> =
        versions.filter { it.status == RuleVersionStatus.ACTIVE }

    fun findVersion(version: Int): RuleVersion? =
        versions.find { it.version == version }

    fun addVersion(dmnXml: DmnXmlContent, createdBy: String): Rule {
        if (status == RuleStatus.INACTIVE) {
            throw BusinessRuleViolationException("Cannot add a version to an INACTIVE rule '${id.value}'")
        }
        val newVersion = RuleVersion(
            ruleId = id,
            version = nextVersionNumber(),
            dmnXml = dmnXml,
            status = RuleVersionStatus.DRAFT,
            createdBy = createdBy,
            createdAt = Instant.now()
        )
        return copy(versions = versions + newVersion, updatedAt = Instant.now())
    }

    fun activateVersion(version: Int, activatedBy: String): Rule {
        if (status == RuleStatus.INACTIVE) {
            throw BusinessRuleViolationException("Cannot activate a version on an INACTIVE rule '${id.value}'")
        }
        val target = findVersion(version)
            ?: throw EntityNotFoundException("RuleVersion", "version=$version for rule ${id.value}")
        if (target.status == RuleVersionStatus.ACTIVE) {
            // Idempotent — already active, no change needed
            return this
        }
        if (target.status != RuleVersionStatus.DRAFT) {
            throw BusinessRuleViolationException(
                "Version $version of rule '${id.value}' is not in DRAFT status (current: ${target.status})"
            )
        }
        val now = Instant.now()
        val updated = versions.map {
            if (it.version == version) it.copy(
                status = RuleVersionStatus.ACTIVE,
                activatedAt = now,
                activatedBy = activatedBy
            ) else it
        }
        return copy(versions = updated, updatedAt = now)
    }

    fun deactivateVersion(version: Int): Rule {
        val target = findVersion(version)
            ?: throw EntityNotFoundException("RuleVersion", "version=$version for rule ${id.value}")
        if (target.status == RuleVersionStatus.INACTIVE) {
            throw BusinessRuleViolationException("Version $version of rule '${id.value}' is already INACTIVE")
        }
        if (target.status != RuleVersionStatus.ACTIVE) {
            throw BusinessRuleViolationException(
                "Version $version of rule '${id.value}' is not ACTIVE (current: ${target.status})"
            )
        }
        val updated = versions.map {
            if (it.version == version) it.copy(status = RuleVersionStatus.INACTIVE) else it
        }
        return copy(versions = updated, updatedAt = Instant.now())
    }

    fun discardVersion(version: Int): Rule {
        val target = findVersion(version)
            ?: throw EntityNotFoundException("RuleVersion", "version=$version for rule ${id.value}")
        if (target.status != RuleVersionStatus.DRAFT) {
            throw BusinessRuleViolationException(
                "Version $version cannot be discarded because it is not in DRAFT status (current: ${target.status})"
            )
        }
        val updated = versions.map {
            if (it.version == version) it.copy(status = RuleVersionStatus.INACTIVE) else it
        }
        return copy(versions = updated, updatedAt = Instant.now())
    }

    fun softDelete(): Rule {
        val updatedVersions = versions.map {
            if (it.status == RuleVersionStatus.ACTIVE || it.status == RuleVersionStatus.DRAFT)
                it.copy(status = RuleVersionStatus.INACTIVE)
            else it
        }
        return copy(status = RuleStatus.INACTIVE, versions = updatedVersions, updatedAt = Instant.now())
    }

    companion object {
        fun create(
            id: RuleId,
            ruleSetId: RuleSetId,
            name: String,
            description: String,
            dmnXml: DmnXmlContent,
            createdBy: String
        ): Rule {
            require(name.isNotBlank()) { "Rule name must not be blank" }
            require(name.length <= 255) { "Rule name must not exceed 255 characters" }
            val now = Instant.now()
            val initialVersion = RuleVersion(
                ruleId = id,
                version = 1,
                dmnXml = dmnXml,
                status = RuleVersionStatus.DRAFT,
                createdBy = createdBy,
                createdAt = now
            )
            return Rule(
                id = id,
                ruleSetId = ruleSetId,
                name = name.trim(),
                description = description,
                status = RuleStatus.ACTIVE,
                versions = listOf(initialVersion),
                createdBy = createdBy,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}
