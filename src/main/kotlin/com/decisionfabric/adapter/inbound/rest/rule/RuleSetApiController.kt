package com.decisionfabric.adapter.inbound.rest.rule

import com.decisionfabric.adapter.inbound.rest.rule.dto.CreateRuleSetRequest
import com.decisionfabric.adapter.inbound.rest.rule.dto.RuleSetListResponse
import com.decisionfabric.adapter.inbound.rest.rule.dto.RuleSetResponse
import com.decisionfabric.application.ports.`in`.RuleManagementUseCase
import com.decisionfabric.application.rule.command.CreateRuleSetCommand
import com.decisionfabric.application.rule.command.DeleteRuleSetCommand
import com.decisionfabric.application.rule.query.RuleSetView
import com.decisionfabric.application.rule.query.PagedRuleSetView
import com.decisionfabric.domain.rule.RuleSetId
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/rule-sets")
class RuleSetApiController(
    private val ruleManagementUseCase: RuleManagementUseCase
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createRuleSet(
        @Valid @RequestBody request: CreateRuleSetRequest,
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader("X-Correlation-ID", defaultValue = "") correlationId: String
    ): RuleSetResponse {
        val view = ruleManagementUseCase.createRuleSet(
            CreateRuleSetCommand(
                name = request.name,
                description = request.description,
                userId = jwt.subject,
                correlationId = correlationId.ifBlank { UUID.randomUUID().toString() }
            )
        )
        return view.toResponse()
    }

    @GetMapping("/{id}")
    fun getRuleSet(@PathVariable id: UUID): RuleSetResponse {
        val view = ruleManagementUseCase.getRuleSet(RuleSetId(id))
        return view.toResponse()
    }

    @GetMapping
    fun listRuleSets(
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
        @RequestParam(defaultValue = "false") includeInactive: Boolean
    ): RuleSetListResponse {
        val paged = ruleManagementUseCase.listRuleSets(page, size, includeInactive)
        return paged.toResponse()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteRuleSet(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader("X-Correlation-ID", defaultValue = "") correlationId: String
    ) {
        ruleManagementUseCase.deleteRuleSet(
            DeleteRuleSetCommand(
                ruleSetId = RuleSetId(id),
                userId = jwt.subject,
                correlationId = correlationId.ifBlank { UUID.randomUUID().toString() }
            )
        )
    }

    // ---- Mapping helpers ----

    private fun RuleSetView.toResponse() = RuleSetResponse(
        id = id.value,
        name = name,
        description = description,
        status = status.name,
        ruleCount = ruleCount,
        activeRuleCount = activeRuleCount,
        createdBy = createdBy,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun PagedRuleSetView.toResponse() = RuleSetListResponse(
        content = content.map { it.toResponse() },
        page = page,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages
    )
}
