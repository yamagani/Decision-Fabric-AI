package com.decisionfabric.domain.rule

import java.util.UUID

@JvmInline
value class RuleSetId(val value: UUID) {
    companion object {
        fun generate(): RuleSetId = RuleSetId(UUID.randomUUID())
        fun from(value: String): RuleSetId = RuleSetId(UUID.fromString(value))
    }

    override fun toString(): String = value.toString()
}
