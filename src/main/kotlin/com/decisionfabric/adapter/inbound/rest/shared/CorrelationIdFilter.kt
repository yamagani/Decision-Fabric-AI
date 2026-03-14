package com.decisionfabric.adapter.inbound.rest.shared

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(1)
class CorrelationIdFilter(
    @Value("\${correlation.header-name:X-Correlation-ID}") private val headerName: String
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val correlationId = request.getHeader(headerName)?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()

        MDC.put("correlationId", correlationId)
        response.setHeader(headerName, correlationId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove("correlationId")
        }
    }
}
