package com.decisionfabric.adapter.outbound.persistence.rule

import com.decisionfabric.application.ports.out.PagedResult
import com.decisionfabric.application.ports.out.RuleRepositoryPort
import com.decisionfabric.domain.rule.Rule
import com.decisionfabric.domain.rule.RuleId
import com.decisionfabric.domain.rule.RuleSet
import com.decisionfabric.domain.rule.RuleSetId
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.math.ceil

@Repository
class PostgreSqlRuleRepositoryAdapter(
    private val ruleSetJpaRepository: RuleSetJpaRepository,
    private val ruleJpaRepository: RuleJpaRepository,
    private val ruleVersionJpaRepository: RuleVersionJpaRepository,
    private val mapper: RuleJpaMapper
) : RuleRepositoryPort {

    // -------------------------------------------------------------------------
    // RuleSet
    // -------------------------------------------------------------------------

    @Transactional
    override fun saveRuleSet(ruleSet: RuleSet): RuleSet {
        val existing = ruleSetJpaRepository.findById(ruleSet.id.value).orElse(null)
        val entity = if (existing != null) mapper.updateEntity(existing, ruleSet)
                     else mapper.toEntity(ruleSet)
        return mapper.toDomain(ruleSetJpaRepository.save(entity))
    }

    @Transactional(readOnly = true)
    override fun findRuleSetById(id: RuleSetId): RuleSet? =
        ruleSetJpaRepository.findById(id.value).map { mapper.toDomain(it) }.orElse(null)

    @Transactional
    override fun findRuleSetByIdForUpdate(id: RuleSetId): RuleSet? =
        ruleJpaRepository.findByIdForUpdate(id.value)
            .map { mapper.toDomain(ruleSetJpaRepository.findById(id.value).get()) }
            .orElse(
                ruleSetJpaRepository.findById(id.value).map { mapper.toDomain(it) }.orElse(null)
            )

    @Transactional(readOnly = true)
    override fun findAllRuleSets(page: Int, size: Int, includeInactive: Boolean): PagedResult<RuleSet> {
        val pageable = PageRequest.of(page, size)
        val result = if (includeInactive) {
            ruleSetJpaRepository.findAllByOrderByCreatedAtDesc(pageable)
        } else {
            ruleSetJpaRepository.findByStatus(RuleSetStatusJpa.ACTIVE, pageable)
        }
        return PagedResult(
            content = result.content.map { mapper.toDomain(it) },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    @Transactional(readOnly = true)
    override fun existsRuleSetByName(name: String): Boolean =
        ruleSetJpaRepository.existsByNameIgnoreCase(name)

    @Transactional(readOnly = true)
    override fun countRulesInSet(ruleSetId: RuleSetId): Int =
        ruleJpaRepository.countByRuleSetId(ruleSetId.value)

    @Transactional(readOnly = true)
    override fun countActiveRulesInSet(ruleSetId: RuleSetId): Int =
        ruleJpaRepository.countActiveByRuleSetId(ruleSetId.value)

    // -------------------------------------------------------------------------
    // Rule
    // -------------------------------------------------------------------------

    @Transactional
    override fun saveRule(rule: Rule): Rule {
        val existing = ruleJpaRepository.findById(rule.id.value).orElse(null)
        val entity = if (existing != null) mapper.updateEntity(existing, rule)
                     else mapper.toEntity(rule)
        return mapper.toDomain(ruleJpaRepository.save(entity))
    }

    @Transactional(readOnly = true)
    override fun findRuleById(id: RuleId): Rule? =
        ruleJpaRepository.findById(id.value).map { mapper.toDomain(it) }.orElse(null)

    @Transactional
    override fun findRuleByIdForUpdate(id: RuleId): Rule? =
        ruleJpaRepository.findByIdForUpdate(id.value).map { mapper.toDomain(it) }.orElse(null)

    @Transactional(readOnly = true)
    override fun findAllRules(
        ruleSetId: RuleSetId?,
        page: Int,
        size: Int,
        search: String?,
        includeInactive: Boolean
    ): PagedResult<Rule> {
        val pageable = PageRequest.of(page, size)
        val result = ruleJpaRepository.search(
            ruleSetId = ruleSetId?.value,
            includeInactive = includeInactive,
            searchPattern = search?.takeIf { it.isNotBlank() }?.let { "%${it.lowercase()}%" },
            pageable = pageable
        )
        return PagedResult(
            content = result.content.map { mapper.toDomain(it) },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    @Transactional(readOnly = true)
    override fun findAllActiveRules(): List<Rule> =
        ruleJpaRepository.findAllActive().map { mapper.toDomain(it) }

    @Transactional(readOnly = true)
    override fun existsRuleByNameInSet(ruleSetId: RuleSetId, name: String, excludeId: RuleId?): Boolean =
        ruleJpaRepository.existsByRuleSetIdAndNameIgnoreCase(
            ruleSetId = ruleSetId.value,
            name = name,
            excludeId = excludeId?.value
        )

    @Transactional
    override fun deleteRuleVersion(ruleId: RuleId, version: Int) {
        ruleVersionJpaRepository.deleteByRuleIdAndVersion(ruleId.value, version)
    }
}
