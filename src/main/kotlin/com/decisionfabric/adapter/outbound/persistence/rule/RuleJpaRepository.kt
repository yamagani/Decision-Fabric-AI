package com.decisionfabric.adapter.outbound.persistence.rule

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface RuleJpaRepository : JpaRepository<RuleJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RuleJpaEntity r WHERE r.id = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): Optional<RuleJpaEntity>

    @Query("""
        SELECT r FROM RuleJpaEntity r
        WHERE (:ruleSetId IS NULL OR r.ruleSetId = :ruleSetId)
          AND (:includeInactive = true OR r.status = 'ACTIVE')
          AND (:search IS NULL OR lower(r.name) LIKE lower(concat('%', :search, '%')))
        ORDER BY r.createdAt DESC
    """)
    fun search(
        @Param("ruleSetId") ruleSetId: UUID?,
        @Param("includeInactive") includeInactive: Boolean,
        @Param("search") search: String?,
        pageable: Pageable
    ): Page<RuleJpaEntity>

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
}
