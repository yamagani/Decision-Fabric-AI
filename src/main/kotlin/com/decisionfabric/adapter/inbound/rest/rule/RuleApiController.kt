package com.decisionfabric.adapter.inbound.rest.rule

import com.decisionfabric.adapter.inbound.rest.rule.dto.CreateRuleRequest
import com.decisionfabric.adapter.inbound.rest.rule.dto.DmnImportResponse
import com.decisionfabric.adapter.inbound.rest.rule.dto.DmnValidationResponse
import com.decisionfabric.adapter.inbound.rest.rule.dto.RuleListResponse
import com.decisionfabric.adapter.inbound.rest.rule.dto.RuleResponse
import com.decisionfabric.adapter.inbound.rest.rule.dto.RuleVersionResponse
import com.decisionfabric.adapter.inbound.rest.rule.dto.UpdateRuleRequest
import com.decisionfabric.application.ports.`in`.RuleManagementUseCase
import com.decisionfabric.application.rule.command.ActivateVersionCommand
import com.decisionfabric.application.rule.command.CreateRuleCommand
import com.decisionfabric.application.rule.command.DeactivateVersionCommand
import com.decisionfabric.application.rule.command.DeleteRuleCommand
import com.decisionfabric.application.rule.command.DiscardVersionCommand
import com.decisionfabric.application.rule.command.ImportDmnCommand
import com.decisionfabric.application.rule.command.PurgeVersionCommand
import com.decisionfabric.application.rule.command.UpdateRuleCommand
import com.decisionfabric.application.rule.command.ValidateDmnCommand
import com.decisionfabric.application.rule.query.PagedRuleView
import com.decisionfabric.application.rule.query.RuleVersionView
import com.decisionfabric.application.rule.query.RuleView
import com.decisionfabric.domain.rule.RuleId
import com.decisionfabric.domain.rule.RuleSetId
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/rules")
class RuleApiController(
    private val ruleManagementUseCase: RuleManagementUseCase
) {

    // ---- CRUD ----

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createRule(
        @Valid @RequestBody request: CreateRuleRequest,
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader("X-Correlation-ID", defaultValue = "") correlationId: String
    ): RuleResponse {
        val view = ruleManagementUseCase.createRule(
            CreateRuleCommand(
                ruleSetId = RuleSetId(request.ruleSetId),
                name = request.name,
                description = request.description,
                dmnXml = request.dmnXml,
                userId = jwt.subject,
                correlationId = correlationId.ifBlank { UUID.randomUUID().toString() }
            )
        )
        return view.toResponse()
    }

    @PutMapping("/{id}")
    fun updateRule(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateRuleRequest,
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader("X-Correlation-ID", defaultValue = "") correlationId: String
    ): RuleResponse {
        val view = ruleManagementUseCase.updateRule(
            UpdateRuleCommand(
                ruleId = RuleId(id),
                name = request.name,
                description = request.description,
                dmnXml = request.dmnXml,
                userId = jwt.subject,
                correlationId = correlationId.ifBlank { UUID.randomUUID().toString() }
            )
        )
        return view.toResponse()
    }

    @GetMapping("/{id}")
    fun getRule(@PathVariable id: UUID): RuleResponse =
        ruleManagementUseCase.getRule(RuleId(id)).toResponse()

    @GetMapping
    fun listRules(
        @RequestParam(required = false) ruleSetId: UUID?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "false") includeInactive: Boolean
    ): RuleListResponse {
        val paged = ruleManagementUseCase.listRules(
            ruleSetId = ruleSetId?.let { RuleSetId(it) },
            page = page,
            size = size,
            search = search,
            includeInactive = includeInactive
        )
        return paged.toResponse()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteRule(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader("X-Correlation-ID", defaultValue = "") correlationId: String
    ) {
        ruleManagementUseCase.deleteRule(
            DeleteRuleCommand(
                ruleId = RuleId(id),
                userId = jwt.subject,
                correlationId = correlationId.ifBlank { UUID.randomUUID().toString() }
            )
        )
    }

    // ---- Version lifecycle ----

    @PostMapping("/{id}/versions/{version}/activate")
    fun activateVersion(
        @PathVariable id: UUID,
        @PathVariable version: Int,
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader("X-Correlation-ID", defaultValue = "") correlationId: String
    ): RuleVersionResponse {
        val view = ruleManagementUseCase.activateVersion(
            ActivateVersionCommand(
                ruleId = RuleId(id),
                version = version,
                userId = jwt.subject,
                correlationId = correlationId.ifBlank { UUID.randomUUID().toString() }
            )
        )
        return view.toResponse()
    }

    @PostMapping("/{id}/versions/{version}/deactivate")
    fun deactivateVersion(
        @PathVariable id: UUID,
        @PathVariable version: Int,
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader("X-Correlation-ID", defaultValue = "") correlationId: String
    ): RuleVersionResponse {
        val view = ruleManagementUseCase.deactivateVersion(
            DeactivateVersionCommand(
                ruleId = RuleId(id),
                version = version,
                userId = jwt.subject,
                correlationId = correlationId.ifBlank { UUID.randomUUID().toString() }
            )
        )
        return view.toResponse()
    }

    @PostMapping("/{id}/versions/{version}/discard")
    fun discardVersion(
        @PathVariable id: UUID,
        @PathVariable version: Int,
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader("X-Correlation-ID", defaultValue = "") correlationId: String
    ): RuleVersionResponse {
        val view = ruleManagementUseCase.discardVersion(
            DiscardVersionCommand(
                ruleId = RuleId(id),
                version = version,
                userId = jwt.subject,
                correlationId = correlationId.ifBlank { UUID.randomUUID().toString() }
            )
        )
        return view.toResponse()
    }

    @DeleteMapping("/{id}/versions/{version}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun purgeVersion(
        @PathVariable id: UUID,
        @PathVariable version: Int,
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader("X-Correlation-ID", defaultValue = "") correlationId: String
    ) {
        ruleManagementUseCase.purgeVersion(
            PurgeVersionCommand(
                ruleId = RuleId(id),
                version = version,
                userId = jwt.subject,
                correlationId = correlationId.ifBlank { UUID.randomUUID().toString() }
            )
        )
    }

    // ---- DMN import / export / validate ----

    @PostMapping("/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun importDmn(
        @RequestPart("file") file: MultipartFile,
        @RequestParam ruleSetId: UUID,
        @RequestParam name: String,
        @RequestParam(defaultValue = "") description: String,
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader("X-Correlation-ID", defaultValue = "") correlationId: String
    ): DmnImportResponse {
        val dmnContent = file.inputStream.bufferedReader(Charsets.UTF_8).readText()
        val view = ruleManagementUseCase.importDmn(
            ImportDmnCommand(
                ruleSetId = RuleSetId(ruleSetId),
                ruleName = name,
                description = description,
                dmnFileContent = dmnContent,
                originalFileName = file.originalFilename ?: "unknown.dmn",
                userId = jwt.subject,
                correlationId = correlationId.ifBlank { UUID.randomUUID().toString() }
            )
        )
        return DmnImportResponse(
            ruleId = view.ruleId.value,
            ruleSetId = view.ruleSetId.value,
            name = view.name,
            version = view.version,
            status = view.status.name
        )
    }

    @GetMapping("/{id}/export")
    fun exportDmn(
        @PathVariable id: UUID,
        @RequestParam(required = false) version: Int?
    ): ResponseEntity<String> {
        val xml = ruleManagementUseCase.exportDmn(RuleId(id), version)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/xml"))
            .body(xml)
    }

    @PostMapping("/validate", consumes = [MediaType.TEXT_XML_VALUE, MediaType.APPLICATION_XML_VALUE])
    fun validateDmn(
        @RequestBody dmnXml: String,
        @RequestHeader("X-Correlation-ID", defaultValue = "") correlationId: String
    ): DmnValidationResponse {
        val result = ruleManagementUseCase.validateDmn(
            ValidateDmnCommand(
                dmnXml = dmnXml,
                correlationId = correlationId.ifBlank { UUID.randomUUID().toString() }
            )
        )
        return DmnValidationResponse(valid = result.valid, errors = result.errors)
    }

    // ---- Mapping helpers ----

    private fun RuleView.toResponse() = RuleResponse(
        id = id.value,
        ruleSetId = ruleSetId.value,
        ruleSetName = ruleSetName,
        name = name,
        description = description,
        status = status.name,
        versions = versions.map { it.toResponse() },
        latestVersion = latestVersion,
        activeVersionCount = activeVersionCount,
        createdBy = createdBy,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun RuleVersionView.toResponse() = RuleVersionResponse(
        version = version,
        status = status.name,
        createdBy = createdBy,
        createdAt = createdAt,
        activatedAt = activatedAt,
        activatedBy = activatedBy
    )

    private fun PagedRuleView.toResponse() = RuleListResponse(
        content = content.map { it.toResponse() },
        page = page,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages
    )
}
