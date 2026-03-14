package com.decisionfabric.application.ports.`in`

import com.decisionfabric.application.ports.out.AuditFilter
import com.decisionfabric.application.ports.out.AuditRecord
import com.decisionfabric.application.ports.out.PagedResult
import java.time.Instant
import java.util.UUID

interface AuditQueryUseCase {
    fun getDecision(id: UUID): AuditRecord
    fun listDecisions(query: AuditQueryCommand): PagedResult<AuditRecord>
}

data class AuditQueryCommand(
    val ruleId: UUID? = null,
    val userId: String? = null,
    val from: Instant? = null,
    val to: Instant? = null,
    val page: Int = 0,
    val size: Int = 20
)
