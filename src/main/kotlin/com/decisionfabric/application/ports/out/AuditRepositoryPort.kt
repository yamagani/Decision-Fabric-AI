package com.decisionfabric.application.ports.out

import java.time.Instant
import java.util.UUID

/**
 * Outbound port for appending and querying decision audit records.
 * Implemented by: PostgreSqlAuditRepositoryAdapter
 */
interface AuditRepositoryPort {
    fun append(record: AuditRecord)
    fun findById(id: UUID): AuditRecord?
    fun findAll(filter: AuditFilter, page: Int, size: Int): PagedResult<AuditRecord>
}

data class AuditRecord(
    val id: UUID,
    val ruleId: UUID?,
    val userId: String,
    val correlationId: String,
    val inputData: Map<String, Any?>,
    val outputs: Map<String, Any?>,
    val aiAugmented: Boolean,
    val aiReasoning: String?,
    val confidenceScore: Double,
    val evaluatedAt: Instant
)

data class AuditFilter(
    val ruleId: UUID? = null,
    val userId: String? = null,
    val from: Instant? = null,
    val to: Instant? = null
)
