package org.happyzion.api.common.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.happyzion.api.common.error.UnauthorizedException
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AdminKeyInterceptor(
    private val adminJwtService: AdminJwtService,
) : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (handler !is HandlerMethod) return true
        if (!requiresAdminAuth(handler)) return true

        val token = extractBearerToken(request)
            ?: throw UnauthorizedException("관리자 인증이 필요합니다.")

        val claims = adminJwtService.parseToken(token)
        request.setAttribute(AdminJwtService.ADMIN_ACCOUNT_ID_ATTR, claims.accountId)
        request.setAttribute(AdminJwtService.ADMIN_ACCOUNT_ROLE_ATTR, claims.role)

        return true
    }

    private fun requiresAdminAuth(handler: HandlerMethod): Boolean =
        handler.getMethodAnnotation(AdminKeyRequired::class.java) != null ||
            handler.beanType.getAnnotation(AdminKeyRequired::class.java) != null

    private fun extractBearerToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        if (!header.startsWith("Bearer ")) return null
        return header.removePrefix("Bearer ").trim().takeIf { it.isNotEmpty() }
    }
}
