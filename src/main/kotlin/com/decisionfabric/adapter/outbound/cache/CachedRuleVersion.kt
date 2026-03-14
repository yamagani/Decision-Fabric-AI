package com.decisionfabric.adapter.outbound.cache

import java.time.Instant
import java.util.UUID

data class CachedRuleVersion(
    val ruleId: UUID,
    val version: Int,
    val dmnXml: String,
    val activatedAt: Instant,
    val estimatedBytes: Long = dmnXml.toByteArray(Charsets.UTF_8).size.toLong()
)
