package com.decisionfabric.adapter.outbound.observability

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.config.MeterFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ObservabilityConfig {

    @Bean
    fun metricsCommonTags(
        @Value("\${spring.application.name}") appName: String,
        @Value("\${ENVIRONMENT:local}") environment: String
    ): MeterRegistryCustomizer<MeterRegistry> = MeterRegistryCustomizer { registry ->
        registry.config()
            .meterFilter(MeterFilter.commonTags(listOf(
                io.micrometer.core.instrument.Tag.of("service", appName),
                io.micrometer.core.instrument.Tag.of("env", environment)
            )))
    }
}
