package com.decisionfabric.adapter.inbound.security

import com.ninjasquad.springmockk.MockkBean
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest
@Import(SecurityConfig::class)
class JwtAuthenticationFilterTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `actuator health is accessible without authentication`() {
        mockMvc.get("/actuator/health").andExpect {
            // Health may return 200 or 503 depending on state — just not 401
            status { isOk() }
        }
    }

    @Test
    fun `authenticated request with rule-admin role can access rules endpoint`() {
        mockMvc.get("/api/v1/rules") {
            with(jwt().authorities(
                org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_RULE_ADMIN")
            ))
        }.andExpect {
            // 404 is fine — endpoint not wired yet; 401/403 would indicate security misconfiguration
            status { isNotFound() }
        }
    }

    @Test
    fun `unauthenticated request to protected endpoint returns 401`() {
        mockMvc.get("/api/v1/rules").andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `decision-consumer cannot access rule write endpoints`() {
        mockMvc.get("/api/v1/config/ai") {
            with(jwt().authorities(
                org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_DECISION_CONSUMER")
            ))
        }.andExpect {
            status { isForbidden() }
        }
    }
}
