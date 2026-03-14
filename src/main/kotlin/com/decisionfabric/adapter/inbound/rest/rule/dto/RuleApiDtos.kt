package com.decisionfabric.adapter.inbound.rest.rule.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant
import java.util.UUID

// ---- Request DTOs ----

data class CreateRuleSetRequest(
    val name: String,
    val description: String = ""
)

data class CreateRuleRequest(
    val ruleSetId: UUID,
    val name: String,
    val description: String = "",
    val dmnXml: String
)

data class UpdateRuleRequest(
    val name: String? = null,
    val description: String? = null,
    val dmnXml: String
)

// ---- Response DTOs ----

data class RuleSetResponse(
    val id: UUID,
    val name: String,
    val description: String,
    val status: String,
    val ruleCount: Int,
    val activeRuleCount: Int,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class RuleSetListResponse(
    val content: List<RuleSetResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class RuleVersionResponse(
    val version: Int,
    val status: String,
    val createdBy: String,
    val createdAt: Instant,
    val activatedAt: Instant?,
    val activatedBy: String?
)

data class RuleResponse(
    val id: UUID,
    val ruleSetId: UUID,
    val ruleSetName: String,
    val name: String,
    val description: String,
    val status: String,
    val versions: List<RuleVersionResponse>,
    val latestVersion: Int,
    val activeVersionCount: Int,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class RuleListResponse(
    val content: List<RuleResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class DmnImportResponse(
    val ruleId: UUID,
    val ruleSetId: UUID,
    val name: String,
    val version: Int,
    val status: String
)

data class DmnValidationResponse(
    val valid: Boolean,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val errors: List<String> = emptyList()
)
