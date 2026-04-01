package com.decisionfabric.application.ports.out

import com.decisionfabric.domain.rule.Rule
import com.decisionfabric.domain.rule.RuleId
import com.decisionfabric.domain.rule.RuleSet
import com.decisionfabric.domain.rule.RuleSetId

/**
 * Outbound port for rule and rule-set persistence.
 * Implemented by: PostgreSqlRuleRepositoryAdapter
 */
interface RuleRepositoryPort {
    // --- RuleSet ---
    fun saveRuleSet(ruleSet: RuleSet): RuleSet
    fun findRuleSetById(id: RuleSetId): RuleSet?
    fun findRuleSetByIdForUpdate(id: RuleSetId): RuleSet?
    fun findAllRuleSets(page: Int, size: Int, includeInactive: Boolean): PagedResult<RuleSet>
    fun existsRuleSetByName(name: String): Boolean
    fun countRulesInSet(ruleSetId: RuleSetId): Int
    fun countActiveRulesInSet(ruleSetId: RuleSetId): Int
    /** Batch fetch (total, active) counts for multiple rule sets in a single query. */
    fun countRulesPerSet(ruleSetIds: List<RuleSetId>): Map<RuleSetId, Pair<Int, Int>>
    /** Batch fetch names for multiple rule sets in a single query. */
    fun findRuleSetNamesByIds(ruleSetIds: List<RuleSetId>): Map<RuleSetId, String>

    // --- Rule ---
    fun saveRule(rule: Rule): Rule
    fun findRuleById(id: RuleId): Rule?
    fun findRuleByIdForUpdate(id: RuleId): Rule?
    fun findAllRules(
        ruleSetId: RuleSetId?,
        page: Int,
        size: Int,
        search: String?,
        includeInactive: Boolean
    ): PagedResult<Rule>
    fun findAllActiveRules(): List<Rule>
    fun existsRuleByNameInSet(ruleSetId: RuleSetId, name: String, excludeId: RuleId? = null): Boolean
    fun deleteRuleVersion(ruleId: RuleId, version: Int)
}

data class PagedResult<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)
