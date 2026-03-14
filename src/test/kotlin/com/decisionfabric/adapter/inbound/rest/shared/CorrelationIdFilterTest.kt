package com.decisionfabric.adapter.inbound.rest.shared

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class CorrelationIdFilterTest {

    private val filter = CorrelationIdFilter("X-Correlation-ID")

    @BeforeEach
    fun setUp() = MDC.clear()

    @AfterEach
    fun tearDown() = MDC.clear()

    @Test
    fun `uses existing correlation ID from request header`() {
        val request = MockHttpServletRequest().apply {
            addHeader("X-Correlation-ID", "test-correlation-id-123")
        }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assert(response.getHeader("X-Correlation-ID") == "test-correlation-id-123")
    }

    @Test
    fun `generates new correlation ID when none provided`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        val correlationId = response.getHeader("X-Correlation-ID")
        assert(!correlationId.isNullOrBlank())
        assert(correlationId!!.length == 36) // UUID length
    }

    @Test
    fun `MDC is cleared after filter completes`() {
        val request = MockHttpServletRequest().apply {
            addHeader("X-Correlation-ID", "abc-123")
        }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assert(MDC.get("correlationId") == null)
    }
}
