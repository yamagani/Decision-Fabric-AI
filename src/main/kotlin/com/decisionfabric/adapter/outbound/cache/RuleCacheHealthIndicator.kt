package com.decisionfabric.adapter.outbound.cache

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Health indicator for InMemoryRuleCache.
 * UP: bytes < 80% capacity
 * DEGRADED: 80% <= bytes < 95% capacity
 * DOWN: bytes >= 95% capacity
 */
@Component
class RuleCacheHealthIndicator(
    private val ruleCache: InMemoryRuleCache,
    @Value("\${rule.cache.max-bytes-mb:200}") maxBytesMb: Long
) : HealthIndicator {

    private val maxBytes = maxBytesMb * 1_048_576L
    private val degradedThreshold = (maxBytes * 0.80).toLong()
    private val downThreshold = (maxBytes * 0.95).toLong()

    override fun health(): Health {
        val usedBytes = ruleCache.getCurrentBytes()
        val entries = ruleCache.size()
        val details = mapOf(
            "cachedRules" to entries,
            "usedBytes" to usedBytes,
            "maxBytes" to maxBytes,
            "utilizationPct" to if (maxBytes > 0) (usedBytes * 100 / maxBytes) else 0
        )

        return when {
            usedBytes >= downThreshold -> Health.down()
                .withDetails(details)
                .withDetail("reason", "Cache usage >= 95% of capacity")
                .build()
            usedBytes >= degradedThreshold -> Health.status("DEGRADED")
                .withDetails(details)
                .withDetail("reason", "Cache usage >= 80% of capacity")
                .build()
            else -> Health.up().withDetails(details).build()
        }
    }
}
