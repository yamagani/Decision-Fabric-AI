package com.decisionfabric.adapter.inbound.rest.rule

import com.decisionfabric.application.ports.`in`.RuleManagementUseCase
import com.decisionfabric.application.rule.query.PagedRuleSetView
import com.decisionfabric.application.rule.query.RuleSetView
import com.decisionfabric.domain.rule.RuleSetId
import com.decisionfabric.domain.rule.RuleSetStatus
import com.decisionfabric.domain.shared.ConflictException
import com.decisionfabric.domain.shared.EntityNotFoundException
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

@WebMvcTest(controllers = [RuleSetApiController::class])
@Import(com.decisionfabric.adapter.inbound.security.SecurityConfig::class)
class RuleSetApiControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var ruleManagementUseCase: RuleManagementUseCase

    private val ruleSetId = RuleSetId(UUID.randomUUID())

    private fun aRuleSetView() = RuleSetView(
        id = ruleSetId,
        name = "Test Set",
        description = "desc",
        status = RuleSetStatus.ACTIVE,
        ruleCount = 0,
        activeRuleCount = 0,
        createdBy = "user1",
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Test
    fun `GET rule-sets requires authentication`() {
        mockMvc.get("/api/v1/rule-sets").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `GET rule-sets returns 200 with RULE_READER role`() {
        every { ruleManagementUseCase.listRuleSets(any(), any(), any()) } returns
            PagedRuleSetView(emptyList(), 0, 20, 0, 0)

        mockMvc.get("/api/v1/rule-sets") {
            with(jwt().authorities(SimpleGrantedAuthority("ROLE_RULE_READER")))
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `GET rule-set by id returns 200`() {
        every { ruleManagementUseCase.getRuleSet(ruleSetId) } returns aRuleSetView()

        mockMvc.get("/api/v1/rule-sets/${ruleSetId.value}") {
            with(jwt().authorities(SimpleGrantedAuthority("ROLE_RULE_READER")))
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Test Set") }
        }
    }

    @Test
    fun `GET rule-set by id returns 404 when not found`() {
        every { ruleManagementUseCase.getRuleSet(ruleSetId) } throws EntityNotFoundException("RuleSet", ruleSetId.value)

        mockMvc.get("/api/v1/rule-sets/${ruleSetId.value}") {
            with(jwt().authorities(SimpleGrantedAuthority("ROLE_RULE_READER")))
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST rule-sets requires RULE_ADMIN`() {
        mockMvc.post("/api/v1/rule-sets") {
            with(jwt().authorities(SimpleGrantedAuthority("ROLE_RULE_READER")))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"New Set"}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `POST rule-sets creates rule set with RULE_ADMIN role`() {
        every { ruleManagementUseCase.createRuleSet(any()) } returns aRuleSetView()

        mockMvc.post("/api/v1/rule-sets") {
            with(jwt().authorities(SimpleGrantedAuthority("ROLE_RULE_ADMIN")))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"New Set","description":"test"}"""
        }.andExpect { status { isCreated() } }
    }

    @Test
    fun `POST rule-sets returns 409 when name conflict`() {
        every { ruleManagementUseCase.createRuleSet(any()) } throws ConflictException("Name exists")

        mockMvc.post("/api/v1/rule-sets") {
            with(jwt().authorities(SimpleGrantedAuthority("ROLE_RULE_ADMIN")))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Existing Set"}"""
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `DELETE rule-set returns 204`() {
        every { ruleManagementUseCase.deleteRuleSet(any()) } returns Unit

        mockMvc.delete("/api/v1/rule-sets/${ruleSetId.value}") {
            with(jwt().authorities(SimpleGrantedAuthority("ROLE_RULE_ADMIN")))
        }.andExpect { status { isNoContent() } }
    }
}
