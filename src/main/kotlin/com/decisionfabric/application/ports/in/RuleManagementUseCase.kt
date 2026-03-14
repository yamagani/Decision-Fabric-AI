package com.decisionfabric.application.ports.`in`

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
import com.decisionfabric.domain.rule.RuleId
import com.decisionfabric.domain.rule.RuleSetId

interface RuleManagementUseCase {
    // Rule Set operations
    fun createRuleSet(command: CreateRuleSetCommand): RuleSetView
    fun getRuleSet(ruleSetId: RuleSetId): RuleSetView
    fun listRuleSets(page: Int, size: Int, includeInactive: Boolean = false): PagedRuleSetView
    fun deleteRuleSet(command: DeleteRuleSetCommand)

    // Rule operations
    fun createRule(command: CreateRuleCommand): RuleView
    fun updateRule(command: UpdateRuleCommand): RuleView
    fun getRule(ruleId: RuleId): RuleView
    fun listRules(
        ruleSetId: RuleSetId?,
        page: Int,
        size: Int,
        search: String? = null,
        includeInactive: Boolean = false
    ): PagedRuleView
    fun deleteRule(command: DeleteRuleCommand)

    // Version operations
    fun activateVersion(command: ActivateVersionCommand): RuleVersionView
    fun deactivateVersion(command: DeactivateVersionCommand): RuleVersionView
    fun discardVersion(command: DiscardVersionCommand): RuleVersionView
    fun purgeVersion(command: PurgeVersionCommand)

    // DMN import / export / validate
    fun importDmn(command: ImportDmnCommand): DmnImportResultView
    fun exportDmn(ruleId: RuleId, version: Int?): String
    fun validateDmn(command: ValidateDmnCommand): DmnValidationResultView
}
