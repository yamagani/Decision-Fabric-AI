package com.decisionfabric.adapter.inbound.rest.shared

import com.decisionfabric.domain.shared.AiUnavailableException
import com.decisionfabric.domain.shared.BusinessRuleViolationException
import com.decisionfabric.domain.shared.ConflictException
import com.decisionfabric.domain.shared.DmnValidationException
import com.decisionfabric.domain.shared.EntityNotFoundException
import com.decisionfabric.domain.shared.ValidationException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleNotFound(ex: EntityNotFoundException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        error(HttpStatus.NOT_FOUND, ex.message ?: "Not found", request)

    @ExceptionHandler(ValidationException::class, MethodArgumentNotValidException::class, IllegalArgumentException::class)
    fun handleValidation(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val message = when (ex) {
            is MethodArgumentNotValidException -> ex.bindingResult.fieldErrors
                .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
            else -> ex.message ?: "Validation failed"
        }
        return error(HttpStatus.BAD_REQUEST, message, request)
    }

    @ExceptionHandler(DmnValidationException::class)
    fun handleDmnValidation(ex: DmnValidationException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        error(HttpStatus.UNPROCESSABLE_ENTITY, ex.message ?: "DMN validation failed", request)

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        error(HttpStatus.CONFLICT, ex.message ?: "Conflict", request)

    @ExceptionHandler(BusinessRuleViolationException::class)
    fun handleBusinessRule(ex: BusinessRuleViolationException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        error(HttpStatus.UNPROCESSABLE_ENTITY, ex.message ?: "Business rule violation", request)

    @ExceptionHandler(AiUnavailableException::class)
    fun handleAiUnavailable(ex: AiUnavailableException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.warn("AI provider unavailable: ${ex.message}")
        return error(HttpStatus.SERVICE_UNAVAILABLE, "AI augmentation is temporarily unavailable", request)
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleUnsupportedMediaType(ex: HttpMediaTypeNotSupportedException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Content-Type: ${ex.contentType}", request)

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        error(HttpStatus.FORBIDDEN, "Access denied", request)

    @ExceptionHandler(Exception::class)
    fun handleGeneral(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception on ${request.method} ${request.requestURI}", ex)
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request)
    }

    private fun error(status: HttpStatus, message: String, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(status).body(
            ErrorResponse(
                status = status.value(),
                error = status.reasonPhrase,
                message = message,
                correlationId = MDC.get("correlationId"),
                path = request.requestURI
            )
        )
}
