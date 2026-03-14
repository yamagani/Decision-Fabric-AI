package com.decisionfabric.adapter.outbound.persistence.rule

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "rule_sets")
class RuleSetJpaEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    var description: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: RuleSetStatusJpa,

    @Column(name = "created_by", nullable = false, length = 255, updatable = false)
    val createdBy: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,

    @Version
    @Column(name = "version")
    var rowVersion: Long = 0
) {
    // JPA requires no-arg constructor
    protected constructor() : this(
        id = UUID.randomUUID(),
        name = "",
        description = "",
        status = RuleSetStatusJpa.ACTIVE,
        createdBy = "",
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
}

enum class RuleSetStatusJpa { ACTIVE, INACTIVE }
