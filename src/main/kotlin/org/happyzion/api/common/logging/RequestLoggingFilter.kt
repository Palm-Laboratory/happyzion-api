package org.happyzion.api.common.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID
import kotlin.system.measureTimeMillis

@Component
class RequestLoggingFilter : OncePerRequestFilter() {
    private val requestLogger = LoggerFactory.getLogger(RequestLoggingFilter::class.java)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return path.startsWith("/actuator")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestPath = buildRequestPath(request)
        val requestId = resolveRequestId(request)

        response.setHeader(REQUEST_ID_HEADER, requestId)
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId)

        val durationMs = withRequestId(requestId) {
            measureTimeMillis {
                filterChain.doFilter(request, response)
            }
        }

        requestLogger.info(
            "[{}] {} {} -> {} ({} ms)",
            requestId,
            request.method,
            requestPath,
            response.status,
            durationMs,
        )
    }

    private fun buildRequestPath(request: HttpServletRequest): String =
        buildRequestPathForTesting(request.requestURI, request.queryString)

    internal fun buildRequestPathForTesting(uri: String, query: String?): String {
        val q = query ?: return uri
        return if (uri.startsWith("/api/v1/admin/members")) {
            "$uri?${redactSensitive(q)}"
        } else {
            "$uri?$q"
        }
    }

    private fun redactSensitive(query: String): String {
        val keys = setOf("name", "phone")
        return query.split('&').joinToString("&") { token ->
            val eq = token.indexOf('=')
            if (eq <= 0) return@joinToString token
            val key = token.substring(0, eq)
            val value = token.substring(eq + 1)
            if (value.isEmpty()) return@joinToString token
            if (keys.contains(key.lowercase())) "$key=[REDACTED]" else token
        }
    }

    private fun resolveRequestId(request: HttpServletRequest): String =
        request.getHeader(REQUEST_ID_HEADER)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: UUID.randomUUID().toString()

    private fun <T> withRequestId(requestId: String, action: () -> T): T {
        MDC.put(MDC_REQUEST_ID_KEY, requestId)
        return try {
            action()
        } finally {
            MDC.remove(MDC_REQUEST_ID_KEY)
        }
    }

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
        const val REQUEST_ID_ATTRIBUTE = "requestId"
        const val MDC_REQUEST_ID_KEY = "requestId"
    }
}
