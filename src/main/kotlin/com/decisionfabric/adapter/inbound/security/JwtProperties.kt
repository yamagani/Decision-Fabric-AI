package com.decisionfabric.adapter.inbound.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "spring.security.oauth2.resourceserver.jwt")
data class JwtProperties(
    val issuerUri: String,
    val jwkSetUri: String,
    val audience: String = "decision-fabric-ai",
    /**
     * Dot-separated path inside the JWT claims to the list of role strings.
     *
     * Examples by provider:
     *   Keycloak        → realm_access.roles
     *   Okta / Auth0    → roles
     *   Azure AD        → roles   (or "groups" if using group claims)
     *   PingFederate    → <custom — configure to match your token claim>
     *
     * The default "roles" covers the most common flat-claim convention.
     * Nested paths are resolved segment by segment (split on '.').
     */
    val rolesClaimPath: String = "roles"
)
