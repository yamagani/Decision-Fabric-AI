package com.decisionfabric.adapter.inbound.rest.shared

import java.time.Instant

data class ErrorResponse(
    val timestamp: Instant = Instant.now(),
    val status: Int,
    val error: String,
    val message: String,
    val correlationId: String?,
    val path: String?
)
