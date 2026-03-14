package com.decisionfabric.domain.rule.event

import com.decisionfabric.domain.rule.RuleId
import com.decisionfabric.domain.rule.RuleSetId
import com.decisionfabric.domain.shared.DomainEvent
import java.time.Instant

sealed class RuleLifecycleEvent : DomainEvent {

    data class RuleCreated(
        override val occurredAt: Instant = Instant.now(),
        val ruleId: RuleId,
        val ruleSetId: RuleSetId,
        val name: String,
        val initialVersion: Int,
        val userId: String,
        val correlationId: String
    ) : RuleLifecycleEvent()

    data class RuleVersionCreated(
        override val occurredAt: Instant = Instant.now(),
        val ruleId: RuleId,
        val newVersion: Int,
        val userId: String,
        val correlationId: String
    ) : RuleLifecycleEvent()

    data class RuleVersionActivated(
        override val occurredAt: Instant = Instant.now(),
        val ruleId: RuleId,
        val version: Int,
        val userId: String,
        val correlationId: String = ""
    ) : RuleLifecycleEvent()

    data class RuleVersionDeactivated(
        override val occurredAt: Instant = Instant.now(),
        val ruleId: RuleId,
        val version: Int,
        val userId: String,
        val correlationId: String = ""
    ) : RuleLifecycleEvent()

    data class RuleDeleted(
        override val occurredAt: Instant = Instant.now(),
        val ruleId: RuleId,
        val cascadedVersions: List<Int>,
        val userId: String,
        val correlationId: String = ""
    ) : RuleLifecycleEvent()

    data class RuleImported(
        override val occurredAt: Instant = Instant.now(),
        val ruleId: RuleId,
        val ruleSetId: RuleSetId,
        val name: String,
        val fileName: String,
        val userId: String,
        val correlationId: String
    ) : RuleLifecycleEvent()
}
