package com.decisionfabric.application.rule.command

import com.decisionfabric.domain.rule.RuleId
import com.decisionfabric.domain.rule.RuleSetId

data class CreateRuleCommand(
    val ruleSetId: RuleSetId,
    val name: String,
    val description: String,
    val dmnXml: String,
    val userId: String,
    val correlationId: String
)

data class UpdateRuleCommand(
    val ruleId: RuleId,
    val name: String?,
    val description: String?,
    val dmnXml: String,
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
    val dmnFileContent: String,
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

data class PurgeVersionCommand(
    val ruleId: RuleId,
    val version: Int,
    val userId: String,
    val correlationId: String
)
