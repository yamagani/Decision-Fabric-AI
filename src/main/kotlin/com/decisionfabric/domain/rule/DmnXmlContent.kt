package com.decisionfabric.domain.rule

import com.decisionfabric.domain.shared.ValueObject

@JvmInline
value class DmnXmlContent(val value: String) : ValueObject {
    init {
        require(value.isNotBlank()) { "DMN XML content must not be blank" }
        require(value.trimStart().startsWith("<")) { "DMN XML content must begin with an XML element" }
    }
}
