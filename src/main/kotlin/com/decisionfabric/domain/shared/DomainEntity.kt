package com.decisionfabric.domain.shared

abstract class DomainEntity<ID> {
    abstract val id: ID

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DomainEntity<*>) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
