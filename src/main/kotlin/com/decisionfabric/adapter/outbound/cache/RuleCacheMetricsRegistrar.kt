package com.decisionfabric.adapter.outbound.cache

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class RuleCacheMetricsRegistrar(
    registry: MeterRegistry,
    ruleCache: InMemoryRuleCache
) {
    init {
        Gauge.builder("rule.cache.size", ruleCache) { it.size().toDouble() }
            .description("Number of rules with at least one active version in the in-memory cache")
            .register(registry)

        Gauge.builder("rule.cache.bytes", ruleCache) { it.getCurrentBytes().toDouble() }
            .description("Estimated bytes consumed by cached DMN XML in the in-memory rule cache")
            .baseUnit("bytes")
            .register(registry)
    }
}
