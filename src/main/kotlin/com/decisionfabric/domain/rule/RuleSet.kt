package com.decisionfabric.domain.rule

import com.decisionfabric.domain.shared.AggregateRoot
import com.decisionfabric.domain.shared.BusinessRuleViolationException
import java.time.Instant

data class RuleSet(
    override val id: RuleSetId,
    val name: String,
    val description: String,
    val status: RuleSetStatus,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant
) : AggregateRoot<RuleSetId>() {

    fun deactivate(): RuleSet {
        if (status == RuleSetStatus.INACTIVE) return this
        return copy(status = RuleSetStatus.INACTIVE, updatedAt = Instant.now())
    }

    companion object {
        fun create(
            id: RuleSetId,
            name: String,
            description: String,
            createdBy: String
        ): RuleSet {
            require(name.isNotBlank()) { "Rule set name must not be blank" }
            require(name.length <= 255) { "Rule set name must not exceed 255 characters" }
            val now = Instant.now()
            return RuleSet(
                id = id,
                name = name.trim(),
                description = description,
                status = RuleSetStatus.ACTIVE,
                createdBy = createdBy,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}
