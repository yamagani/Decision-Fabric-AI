package com.decisionfabric.adapter.outbound.persistence.rule

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface RuleJpaRepository : JpaRepository<RuleJpaEntity, UUID> {

    @EntityGraph(attributePaths = ["versionEntities"])
    override fun findById(id: UUID): Optional<RuleJpaEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = ["versionEntities"])
    @Query("SELECT r FROM RuleJpaEntity r WHERE r.id = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): Optional<RuleJpaEntity>

    @EntityGraph(attributePaths = ["versionEntities"])
    @Query("""
        SELECT r FROM RuleJpaEntity r
        WHERE (:ruleSetId IS NULL OR r.ruleSetId = :ruleSetId)
          AND (:includeInactive = true OR r.status = 'ACTIVE')
          AND (:searchPattern IS NULL OR lower(r.name) LIKE :searchPattern)
        ORDER BY r.createdAt DESC
    """)
    fun search(
        @Param("ruleSetId") ruleSetId: UUID?,
        @Param("includeInactive") includeInactive: Boolean,
        @Param("searchPattern") searchPattern: String?,
        pageable: Pageable
    ): Page<RuleJpaEntity>

    @EntityGraph(attributePaths = ["versionEntities"])
    @Query("SELECT r FROM RuleJpaEntity r WHERE r.status = 'ACTIVE'")
    fun findAllActive(): List<RuleJpaEntity>

    @Query("""
        SELECT COUNT(r) > 0 FROM RuleJpaEntity r
        WHERE r.ruleSetId = :ruleSetId
          AND lower(r.name) = lower(:name)
          AND (:excludeId IS NULL OR r.id <> :excludeId)
    """)
    fun existsByRuleSetIdAndNameIgnoreCase(
        @Param("ruleSetId") ruleSetId: UUID,
        @Param("name") name: String,
        @Param("excludeId") excludeId: UUID?
    ): Boolean

    @Query("SELECT COUNT(r) FROM RuleJpaEntity r WHERE r.ruleSetId = :ruleSetId")
    fun countByRuleSetId(@Param("ruleSetId") ruleSetId: UUID): Int

    @Query("SELECT COUNT(r) FROM RuleJpaEntity r WHERE r.ruleSetId = :ruleSetId AND r.status = 'ACTIVE'")
    fun countActiveByRuleSetId(@Param("ruleSetId") ruleSetId: UUID): Int

    @Query("""
        SELECT r.ruleSetId as ruleSetId,
               COUNT(r) as total,
               SUM(CASE WHEN r.status = 'ACTIVE' THEN 1L ELSE 0L END) as active
        FROM RuleJpaEntity r
        WHERE r.ruleSetId IN :ruleSetIds
        GROUP BY r.ruleSetId
    """)
    fun countsByRuleSetIds(@Param("ruleSetIds") ruleSetIds: List<UUID>): List<RuleCountProjection>
}

interface RuleCountProjection {
    fun getRuleSetId(): UUID
    fun getTotal(): Long
    fun getActive(): Long
}
