package com.decisionfabric.application.ports.out

import java.util.UUID

/**
 * Outbound port for rule persistence.
 * Implemented by: PostgreSqlRuleRepositoryAdapter
 */
interface RuleRepositoryPort {
    fun save(rule: RuleData): RuleData
    fun findById(id: UUID): RuleData?
    fun findAll(page: Int, size: Int): PagedResult<RuleData>
    fun findAllActive(): List<RuleData>
    fun findByIdAndVersion(id: UUID, version: Int): RuleData?
    fun deleteById(id: UUID)
    fun existsById(id: UUID): Boolean
}

data class RuleData(
    val id: UUID,
    val name: String,
    val description: String?,
    val dmnXml: String,
    val version: Int,
    val status: String,
    val createdBy: String,
    val createdAt: java.time.Instant,
    val updatedAt: java.time.Instant
)

data class PagedResult<T>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int
)
