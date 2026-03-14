package com.decisionfabric.adapter.outbound.persistence.rule

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

data class RuleVersionId(
    val ruleId: UUID = UUID.randomUUID(),
    val version: Int = 0
) : Serializable

@Entity
@IdClass(RuleVersionId::class)
@Table(name = "rule_versions")
class RuleVersionJpaEntity(

    @Id
    @Column(name = "rule_id", nullable = false, updatable = false)
    val ruleId: UUID,

    @Id
    @Column(name = "version", nullable = false, updatable = false)
    val version: Int,

    @Column(name = "dmn_xml", nullable = false, columnDefinition = "TEXT")
    val dmnXml: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: RuleVersionStatusJpa,

    @Column(name = "created_by", nullable = false, length = 255, updatable = false)
    val createdBy: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(name = "activated_at")
    var activatedAt: Instant? = null,

    @Column(name = "activated_by", length = 255)
    var activatedBy: String? = null
) {
    protected constructor() : this(
        ruleId = UUID.randomUUID(),
        version = 0,
        dmnXml = "",
        status = RuleVersionStatusJpa.DRAFT,
        createdBy = "",
        createdAt = Instant.now()
    )
}

enum class RuleVersionStatusJpa { DRAFT, ACTIVE, INACTIVE }
