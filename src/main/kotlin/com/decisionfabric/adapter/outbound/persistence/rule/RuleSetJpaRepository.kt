package com.decisionfabric.adapter.outbound.persistence.rule

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface RuleSetJpaRepository : JpaRepository<RuleSetJpaEntity, UUID> {

    fun findByStatus(status: RuleSetStatusJpa, pageable: Pageable): Page<RuleSetJpaEntity>

    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<RuleSetJpaEntity>

    @Query("SELECT COUNT(r) > 0 FROM RuleSetJpaEntity r WHERE lower(r.name) = lower(:name)")
    fun existsByNameIgnoreCase(@Param("name") name: String): Boolean
}
