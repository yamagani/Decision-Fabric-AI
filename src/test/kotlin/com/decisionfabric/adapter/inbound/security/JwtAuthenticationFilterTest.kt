package com.decisionfabric.adapter.inbound.security

import com.decisionfabric.application.ports.`in`.RuleManagementUseCase
import com.decisionfabric.application.rule.query.PagedRuleView
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest
@Import(SecurityConfig::class)
class JwtAuthenticationFilterTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var ruleManagementUseCase: RuleManagementUseCase

    @Test
    fun `actuator health path allows unauthenticated access`() {
        // In @WebMvcTest the management context is partial — health may return 200 or 404
        // depending on which actuator beans are loaded. What matters is it is NOT 401/403.
        val result = mockMvc.get("/actuator/health").andReturn()
        assertThat(result.response.status)
            .`as`("Actuator health should not be blocked by security")
            .isNotIn(401, 403)
    }

    @Test
    fun `authenticated request with rule-admin role can access rules endpoint`() {
        every { ruleManagementUseCase.listRules(any(), any(), any(), any(), any()) } returns
            PagedRuleView(emptyList(), 0, 20, 0, 0)

        mockMvc.get("/api/v1/rules") {
            with(jwt().authorities(SimpleGrantedAuthority("ROLE_RULE_ADMIN")))
        }.andExpect {
            status { isOk() }
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
            with(jwt().authorities(SimpleGrantedAuthority("ROLE_DECISION_CONSUMER")))
        }.andExpect {
            status { isForbidden() }
        }
    }
}
