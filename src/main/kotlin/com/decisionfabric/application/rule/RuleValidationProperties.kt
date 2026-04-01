package com.decisionfabric.application.rule

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "rule.validation")
data class RuleValidationProperties(
    var maxDmnSizeBytes: Long = 1_048_576L
)
