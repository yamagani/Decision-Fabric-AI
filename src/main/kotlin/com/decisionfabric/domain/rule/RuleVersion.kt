package com.decisionfabric.domain.rule

import java.time.Instant

data class RuleVersion(
    val ruleId: RuleId,
    val version: Int,
    val dmnXml: DmnXmlContent,
    val status: RuleVersionStatus,
    val createdBy: String,
    val createdAt: Instant,
    val activatedAt: Instant? = null,
    val activatedBy: String? = null
) {
    init {
        require(version >= 1) { "Version number must be >= 1" }
    }
}
