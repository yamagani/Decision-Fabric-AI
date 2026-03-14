package com.decisionfabric.domain.rule

import java.util.UUID

@JvmInline
value class RuleId(val value: UUID) {
    companion object {
        fun generate(): RuleId = RuleId(UUID.randomUUID())
        fun from(value: String): RuleId = RuleId(UUID.fromString(value))
    }

    override fun toString(): String = value.toString()
}
