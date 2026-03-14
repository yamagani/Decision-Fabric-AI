package com.decisionfabric.adapter.inbound.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "spring.security.oauth2.resourceserver.jwt")
data class JwtProperties(
    val issuerUri: String,
    val jwkSetUri: String,
    val audience: String = "decision-fabric-ai"
)
