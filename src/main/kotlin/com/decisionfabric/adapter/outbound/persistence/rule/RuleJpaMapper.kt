package com.decisionfabric.adapter.outbound.persistence.rule

import com.decisionfabric.domain.rule.DmnXmlContent
import com.decisionfabric.domain.rule.Rule
import com.decisionfabric.domain.rule.RuleId
import com.decisionfabric.domain.rule.RuleSet
import com.decisionfabric.domain.rule.RuleSetId
import com.decisionfabric.domain.rule.RuleSetStatus
import com.decisionfabric.domain.rule.RuleStatus
import com.decisionfabric.domain.rule.RuleVersion
import com.decisionfabric.domain.rule.RuleVersionStatus
import org.springframework.stereotype.Component

@Component
class RuleJpaMapper {

    // ---- RuleSet ----

    fun toEntity(ruleSet: RuleSet): RuleSetJpaEntity =
        RuleSetJpaEntity(
            id = ruleSet.id.value,
            name = ruleSet.name,
            description = ruleSet.description,
            status = ruleSet.status.toJpa(),
            createdBy = ruleSet.createdBy,
            createdAt = ruleSet.createdAt,
            updatedAt = ruleSet.updatedAt
        )

    fun updateEntity(entity: RuleSetJpaEntity, ruleSet: RuleSet): RuleSetJpaEntity {
        entity.name = ruleSet.name
        entity.description = ruleSet.description
        entity.status = ruleSet.status.toJpa()
        entity.updatedAt = ruleSet.updatedAt
        return entity
    }

    fun toDomain(entity: RuleSetJpaEntity): RuleSet =
        RuleSet(
            id = RuleSetId(entity.id),
            name = entity.name,
            description = entity.description,
            status = entity.status.toDomain(),
            createdBy = entity.createdBy,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )

    // ---- Rule ----

    fun toEntity(rule: Rule): RuleJpaEntity {
        val entity = RuleJpaEntity(
            id = rule.id.value,
            ruleSetId = rule.ruleSetId.value,
            name = rule.name,
            description = rule.description,
            status = rule.status.toJpa(),
            createdBy = rule.createdBy,
            createdAt = rule.createdAt,
            updatedAt = rule.updatedAt
        )
        entity.versionEntities.addAll(rule.versions.map { toVersionEntity(it) })
        return entity
    }

    fun updateEntity(entity: RuleJpaEntity, rule: Rule): RuleJpaEntity {
        entity.name = rule.name
        entity.description = rule.description
        entity.status = rule.status.toJpa()
        entity.updatedAt = rule.updatedAt

        // Sync versions: add new, update existing
        val existingByVersion = entity.versionEntities.associateBy { it.version }.toMutableMap()
        entity.versionEntities.clear()
        for (domainVersion in rule.versions) {
            val existing = existingByVersion[domainVersion.version]
            if (existing != null) {
                existing.status = domainVersion.status.toJpa()
                existing.activatedAt = domainVersion.activatedAt
                existing.activatedBy = domainVersion.activatedBy
                entity.versionEntities.add(existing)
            } else {
                entity.versionEntities.add(toVersionEntity(domainVersion))
            }
        }
        return entity
    }

    fun toDomain(entity: RuleJpaEntity): Rule =
        Rule(
            id = RuleId(entity.id),
            ruleSetId = RuleSetId(entity.ruleSetId),
            name = entity.name,
            description = entity.description,
            status = entity.status.toDomain(),
            versions = entity.versionEntities.map { toDomainVersion(it) },
            createdBy = entity.createdBy,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )

    private fun toVersionEntity(version: RuleVersion): RuleVersionJpaEntity =
        RuleVersionJpaEntity(
            ruleId = version.ruleId.value,
            version = version.version,
            dmnXml = version.dmnXml.value,
            status = version.status.toJpa(),
            createdBy = version.createdBy,
            createdAt = version.createdAt,
            activatedAt = version.activatedAt,
            activatedBy = version.activatedBy
        )

    private fun toDomainVersion(entity: RuleVersionJpaEntity): RuleVersion =
        RuleVersion(
            ruleId = RuleId(entity.ruleId),
            version = entity.version,
            dmnXml = DmnXmlContent(entity.dmnXml),
            status = entity.status.toDomain(),
            createdBy = entity.createdBy,
            createdAt = entity.createdAt,
            activatedAt = entity.activatedAt,
            activatedBy = entity.activatedBy
        )

    // ---- Status conversions ----

    private fun RuleSetStatus.toJpa() = when (this) {
        RuleSetStatus.ACTIVE -> RuleSetStatusJpa.ACTIVE
        RuleSetStatus.INACTIVE -> RuleSetStatusJpa.INACTIVE
    }

    private fun RuleSetStatusJpa.toDomain() = when (this) {
        RuleSetStatusJpa.ACTIVE -> RuleSetStatus.ACTIVE
        RuleSetStatusJpa.INACTIVE -> RuleSetStatus.INACTIVE
    }

    private fun RuleStatus.toJpa() = when (this) {
        RuleStatus.ACTIVE -> RuleStatusJpa.ACTIVE
        RuleStatus.INACTIVE -> RuleStatusJpa.INACTIVE
    }

    private fun RuleStatusJpa.toDomain() = when (this) {
        RuleStatusJpa.ACTIVE -> RuleStatus.ACTIVE
        RuleStatusJpa.INACTIVE -> RuleStatus.INACTIVE
    }

    private fun RuleVersionStatus.toJpa() = when (this) {
        RuleVersionStatus.DRAFT -> RuleVersionStatusJpa.DRAFT
        RuleVersionStatus.ACTIVE -> RuleVersionStatusJpa.ACTIVE
        RuleVersionStatus.INACTIVE -> RuleVersionStatusJpa.INACTIVE
    }

    private fun RuleVersionStatusJpa.toDomain() = when (this) {
        RuleVersionStatusJpa.DRAFT -> RuleVersionStatus.DRAFT
        RuleVersionStatusJpa.ACTIVE -> RuleVersionStatus.ACTIVE
        RuleVersionStatusJpa.INACTIVE -> RuleVersionStatus.INACTIVE
    }
}
