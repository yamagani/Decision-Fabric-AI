package com.decisionfabric.domain.rule

import com.decisionfabric.domain.shared.ValueObject

data class RuleReference(
    val ruleId: RuleId,
    val name: String,
    val status: RuleStatus,
    val activeVersionCount: Int
) : ValueObject
