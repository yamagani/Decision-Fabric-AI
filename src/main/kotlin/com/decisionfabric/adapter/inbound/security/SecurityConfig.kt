package com.decisionfabric.adapter.inbound.security

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties::class)
class SecurityConfig(private val jwtProperties: JwtProperties) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // Actuator health — open to monitoring agents
                    .requestMatchers("/actuator/health").permitAll()
                    // Decision evaluation — role: decision-consumer or higher
                    .requestMatchers(HttpMethod.POST, "/api/v1/decisions/**")
                        .hasAnyRole("DECISION_CONSUMER", "RULE_ADMIN", "SYSTEM_ADMIN")
                    // Decision history queries — role: audit-reader
                    .requestMatchers(HttpMethod.GET, "/api/v1/decisions/history/**")
                        .hasAnyRole("AUDIT_READER", "SYSTEM_ADMIN")
                    // Rule-set operations
                    .requestMatchers(HttpMethod.GET, "/api/v1/rule-sets/**")
                        .hasAnyRole("RULE_READER", "RULE_ADMIN", "SYSTEM_ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/rule-sets/**")
                        .hasAnyRole("RULE_ADMIN", "SYSTEM_ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/rule-sets/**")
                        .hasAnyRole("RULE_ADMIN", "SYSTEM_ADMIN")
                    // Rule read operations — role: rule-reader or higher
                    .requestMatchers(HttpMethod.GET, "/api/v1/rules/**")
                        .hasAnyRole("RULE_READER", "RULE_ADMIN", "SYSTEM_ADMIN")
                    // DMN validate (POST but read-only semantics) — rule-reader or higher
                    .requestMatchers(HttpMethod.POST, "/api/v1/rules/validate")
                        .hasAnyRole("RULE_READER", "RULE_ADMIN", "SYSTEM_ADMIN")
                    // Purge versions — SYSTEM_ADMIN only
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/rules/{id}/versions/{version}")
                        .hasRole("SYSTEM_ADMIN")
                    // Rule write operations — role: rule-admin
                    .requestMatchers(HttpMethod.POST, "/api/v1/rules", "/api/v1/rules/**")
                        .hasAnyRole("RULE_ADMIN", "SYSTEM_ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/rules", "/api/v1/rules/**")
                        .hasAnyRole("RULE_ADMIN", "SYSTEM_ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/rules", "/api/v1/rules/**")
                        .hasAnyRole("RULE_ADMIN", "SYSTEM_ADMIN")
                    // AI config — role: system-admin
                    .requestMatchers("/api/v1/config/**")
                        .hasRole("SYSTEM_ADMIN")
                    // All other actuator endpoints — authenticated only
                    .requestMatchers("/actuator/**").authenticated()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
            }

        return http.build()
    }

    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        return JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter { jwt ->
                extractRoles(jwt.claims, jwtProperties.rolesClaimPath)
                    .map { SimpleGrantedAuthority("ROLE_$it") }
            }
        }
    }

    /**
     * Resolves a dot-separated [claimPath] against the JWT [claims] map and
     * returns the resulting list of role strings, or an empty list if the path
     * does not exist or does not resolve to a collection.
     *
     * Single-segment paths (e.g. "roles")  → looks up claims["roles"]
     * Multi-segment paths (e.g. "realm_access.roles") → follows each segment
     * in turn, expecting intermediate nodes to be Map<String, Any>.
     */
    private fun extractRoles(claims: Map<String, Any>, claimPath: String): Collection<String> {
        val segments = claimPath.split(".")
        var node: Any? = claims
        for (segment in segments) {
            node = (node as? Map<*, *>)?.get(segment) ?: return emptyList()
        }
        @Suppress("UNCHECKED_CAST")
        return node as? Collection<String> ?: emptyList()
    }
}
