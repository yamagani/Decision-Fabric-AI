package com.decisionfabric.adapter.outbound.persistence.rule

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface RuleVersionJpaRepository : JpaRepository<RuleVersionJpaEntity, RuleVersionId> {

    @Query("SELECT v FROM RuleVersionJpaEntity v WHERE v.ruleId = :ruleId AND v.status = 'ACTIVE' ORDER BY v.version DESC")
    fun findActiveVersionsByRuleId(@Param("ruleId") ruleId: UUID): List<RuleVersionJpaEntity>

    @Modifying
    @Query("DELETE FROM RuleVersionJpaEntity v WHERE v.ruleId = :ruleId AND v.version = :version")
    fun deleteByRuleIdAndVersion(@Param("ruleId") ruleId: UUID, @Param("version") version: Int)
}
