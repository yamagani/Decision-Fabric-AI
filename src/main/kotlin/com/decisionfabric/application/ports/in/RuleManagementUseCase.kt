package com.decisionfabric.application.ports.`in`

import com.decisionfabric.application.ports.out.PagedResult
import com.decisionfabric.application.ports.out.RuleData
import java.util.UUID

interface RuleManagementUseCase {
    fun createRule(command: CreateRuleCommand): RuleData
    fun updateRule(command: UpdateRuleCommand): RuleData
    fun activateVersion(command: ActivateVersionCommand): RuleData
    fun deactivateRule(id: UUID, userId: String): RuleData
    fun importDmn(command: ImportDmnCommand): List<RuleData>
    fun exportDmn(id: UUID): String
    fun getRule(id: UUID): RuleData
    fun listRules(page: Int, size: Int): PagedResult<RuleData>
}

data class CreateRuleCommand(
    val name: String,
    val description: String?,
    val dmnXml: String,
    val userId: String
)

data class UpdateRuleCommand(
    val id: UUID,
    val name: String?,
    val description: String?,
    val dmnXml: String?,
    val userId: String
)

data class ActivateVersionCommand(
    val id: UUID,
    val version: Int,
    val userId: String
)

data class ImportDmnCommand(
    val dmnXmlBytes: ByteArray,
    val userId: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImportDmnCommand) return false
        return dmnXmlBytes.contentEquals(other.dmnXmlBytes) && userId == other.userId
    }

    override fun hashCode(): Int = 31 * dmnXmlBytes.contentHashCode() + userId.hashCode()
}
