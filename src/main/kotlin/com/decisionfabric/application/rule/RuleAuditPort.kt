package com.decisionfabric.application.rule

import java.time.Instant
import java.util.UUID

data class RuleAuditRecord(
    val entityId: String,
    val entityType: String,
    val action: RuleAuditAction,
    val performedBy: String,
    val correlationId: String,
    val detail: String,
    val occurredAt: Instant = Instant.now()
)

interface RuleAuditPort {
    fun append(record: RuleAuditRecord)
}
