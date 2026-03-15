package com.decisionfabric.adapter.inbound.rest.rule

import com.decisionfabric.application.ports.`in`.RuleManagementUseCase
import com.decisionfabric.application.rule.query.RuleVersionView
import com.decisionfabric.application.rule.query.RuleView
import com.decisionfabric.application.rule.query.PagedRuleView
import com.decisionfabric.domain.rule.RuleId
import com.decisionfabric.domain.rule.RuleSetId
import com.decisionfabric.domain.rule.RuleStatus
import com.decisionfabric.domain.rule.RuleVersionStatus
import com.decisionfabric.domain.shared.EntityNotFoundException
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Instant
import java.util.UUID

@WebMvcTest(controllers = [RuleApiController::class])
@Import(com.decisionfabric.adapter.inbound.security.SecurityConfig::class)
class RuleApiControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var ruleManagementUseCase: RuleManagementUseCase

    private val ruleId = RuleId(UUID.randomUUID())
    private val ruleSetId = RuleSetId(UUID.randomUUID())

    private fun aRuleView() = RuleView(
        id = ruleId,
        ruleSetId = ruleSetId,
        ruleSetName = "Test Set",
        name = "Test Rule",
        description = "desc",
        status = RuleStatus.ACTIVE,
        versions = listOf(
            RuleVersionView(1, RuleVersionStatus.DRAFT, "user1", Instant.now(), null, null)
        ),
        latestVersion = 1,
        activeVersionCount = 0,
        createdBy = "user1",
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Test
    fun `GET rules requires authentication`() {
        mockMvc.get("/api/v1/rules").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `GET rules returns 200 with RULE_READER role`() {
        every { ruleManagementUseCase.listRules(any(), any(), any(), any(), any()) } returns
            PagedRuleView(emptyList(), 0, 20, 0, 0)

        mockMvc.get("/api/v1/rules") {
            with(jwt().authorities(
                org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_RULE_READER")
            ))
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `GET rule by id returns 200`() {
        every { ruleManagementUseCase.getRule(ruleId) } returns aRuleView()

        mockMvc.get("/api/v1/rules/${ruleId.value}") {
            with(jwt().authorities(
                org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_RULE_READER")
            ))
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Test Rule") }
        }
    }

    @Test
    fun `GET rule by id returns 404 when not found`() {
        every { ruleManagementUseCase.getRule(ruleId) } throws EntityNotFoundException("Rule", ruleId.value)

        mockMvc.get("/api/v1/rules/${ruleId.value}") {
            with(jwt().authorities(
                org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_RULE_READER")
            ))
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST rules requires RULE_ADMIN role`() {
        mockMvc.post("/api/v1/rules") {
            with(jwt().authorities(
                org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_RULE_READER")
            ))
            contentType = MediaType.APPLICATION_JSON
            content = """{"ruleSetId":"${ruleSetId.value}","name":"Test","dmnXml":"<x/>"}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `DELETE versions endpoint requires SYSTEM_ADMIN role`() {
        mockMvc.delete("/api/v1/rules/${ruleId.value}/versions/1") {
            with(jwt().authorities(
                org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_RULE_ADMIN")
            ))
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `DELETE versions endpoint allowed for SYSTEM_ADMIN`() {
        every { ruleManagementUseCase.purgeVersion(any()) } returns Unit

        mockMvc.delete("/api/v1/rules/${ruleId.value}/versions/1") {
            with(jwt().authorities(
                org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")
            ))
        }.andExpect { status { isNoContent() } }
    }
}
