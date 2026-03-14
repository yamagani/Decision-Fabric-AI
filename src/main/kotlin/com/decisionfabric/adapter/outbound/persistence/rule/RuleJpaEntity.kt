package com.decisionfabric.adapter.outbound.persistence.rule

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "rules")
class RuleJpaEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "rule_set_id", nullable = false, updatable = false)
    val ruleSetId: UUID,

    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    var description: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: RuleStatusJpa,

    @Column(name = "created_by", nullable = false, length = 255, updatable = false)
    val createdBy: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,

    @Version
    @Column(name = "row_version")
    var rowVersion: Long = 0,

    @OneToMany(
        mappedBy = "ruleId",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.EAGER
    )
    @OrderBy("version ASC")
    val versionEntities: MutableList<RuleVersionJpaEntity> = mutableListOf()
) {
    protected constructor() : this(
        id = UUID.randomUUID(),
        ruleSetId = UUID.randomUUID(),
        name = "",
        description = "",
        status = RuleStatusJpa.ACTIVE,
        createdBy = "",
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
}

enum class RuleStatusJpa { ACTIVE, INACTIVE }
