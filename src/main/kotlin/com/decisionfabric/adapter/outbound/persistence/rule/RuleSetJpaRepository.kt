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

interface RuleSetJpaRepository : JpaRepository<RuleSetJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RuleSetJpaEntity r WHERE r.id = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): Optional<RuleSetJpaEntity>

    fun findByStatus(status: RuleSetStatusJpa, pageable: Pageable): Page<RuleSetJpaEntity>

    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<RuleSetJpaEntity>

    @Query("SELECT COUNT(r) > 0 FROM RuleSetJpaEntity r WHERE lower(r.name) = lower(:name)")
    fun existsByNameIgnoreCase(@Param("name") name: String): Boolean
}
