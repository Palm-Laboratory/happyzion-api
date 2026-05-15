package org.happyzion.api.common.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.happyzion.api.common.config.AdminProperties
import org.happyzion.api.common.error.ForbiddenException
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AdminKeyInterceptor(
    private val adminProperties: AdminProperties,
) : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (handler !is HandlerMethod) {
            return true
        }
        if (!requiresAdminKey(handler)) {
            return true
        }

        validateAdminKey(request.getHeader(HEADER_NAME))
        return true
    }

    private fun requiresAdminKey(handler: HandlerMethod): Boolean =
        handler.getMethodAnnotation(AdminKeyRequired::class.java) != null ||
            handler.beanType.getAnnotation(AdminKeyRequired::class.java) != null

    private fun validateAdminKey(adminKey: String?) {
        val configuredKey = adminProperties.syncKey.trim()
        if (configuredKey.isBlank()) {
            throw IllegalStateException("ADMIN_SYNC_KEY is not configured.")
        }

        if (adminKey.isNullOrBlank() || adminKey != configuredKey) {
            throw ForbiddenException("관리자 키가 올바르지 않습니다.")
        }
    }

    companion object {
        const val HEADER_NAME = "X-Admin-Key"
    }
}
