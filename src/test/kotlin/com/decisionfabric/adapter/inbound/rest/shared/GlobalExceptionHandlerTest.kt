package com.decisionfabric.adapter.inbound.rest.shared

import com.decisionfabric.domain.shared.ConflictException
import com.decisionfabric.domain.shared.DmnValidationException
import com.decisionfabric.domain.shared.EntityNotFoundException
import com.decisionfabric.domain.shared.ValidationException
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()
    private val request = MockHttpServletRequest().apply { requestURI = "/api/v1/rules" }

    @Test
    fun `EntityNotFoundException maps to 404`() {
        val response = handler.handleNotFound(EntityNotFoundException("Rule", "abc"), request)
        assert(response.statusCode.value() == 404)
        assert(response.body?.error == "Not Found")
    }

    @Test
    fun `ValidationException maps to 400`() {
        val response = handler.handleValidation(ValidationException("name is required"), request)
        assert(response.statusCode.value() == 400)
        assert(response.body?.message == "name is required")
    }

    @Test
    fun `DmnValidationException maps to 422`() {
        val response = handler.handleDmnValidation(DmnValidationException("Invalid DMN schema"), request)
        assert(response.statusCode.value() == 422)
    }

    @Test
    fun `ConflictException maps to 409`() {
        val response = handler.handleConflict(ConflictException("Rule already exists"), request)
        assert(response.statusCode.value() == 409)
    }

    @Test
    fun `error response includes path`() {
        val response = handler.handleNotFound(EntityNotFoundException("Rule", "xyz"), request)
        assert(response.body?.path == "/api/v1/rules")
    }
}
