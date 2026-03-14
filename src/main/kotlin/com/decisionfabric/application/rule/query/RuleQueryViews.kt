package com.decisionfabric.application.rule.query

import com.decisionfabric.domain.rule.RuleId
import com.decisionfabric.domain.rule.RuleSetId
import com.decisionfabric.domain.rule.RuleSetStatus
import com.decisionfabric.domain.rule.RuleStatus
import com.decisionfabric.domain.rule.RuleVersionStatus
import java.time.Instant

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

data class PagedRuleView(
    val content: List<RuleView>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class PagedRuleSetView(
    val content: List<RuleSetView>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)
