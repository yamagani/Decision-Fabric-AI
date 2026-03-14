package com.decisionfabric.domain.shared

import java.time.Instant

interface DomainEvent {
    val occurredAt: Instant
}
