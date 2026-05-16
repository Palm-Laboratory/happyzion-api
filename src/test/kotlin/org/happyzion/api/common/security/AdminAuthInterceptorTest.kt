package org.happyzion.api.common.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.happyzion.api.adminaccount.domain.AdminAccountRole
import org.happyzion.api.common.error.UnauthorizedException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.method.HandlerMethod

class AdminAuthInterceptorTest {

    private val adminJwtService: AdminJwtService = mock()
    private val interceptor = AdminAuthInterceptor(adminJwtService)
    private val response: HttpServletResponse = mock()

    @Test
    fun `passes through when handler is not a HandlerMethod`() {
        val request: HttpServletRequest = mock()

        val result = interceptor.preHandle(request, response, Any())

        assertThat(result).isTrue()
    }

    @Test
    fun `passes through when handler has no AdminAuthRequired annotation`() {
        val request: HttpServletRequest = mock()
        val handler = handlerMethodFor(SampleController::class.java, "open")

        val result = interceptor.preHandle(request, response, handler)

        assertThat(result).isTrue()
    }

    @Test
    fun `throws UnauthorizedException when bearer token is missing`() {
        val request: HttpServletRequest = mock()
        val handler = handlerMethodFor(AnnotatedController::class.java, "anyEndpoint")

        assertThrows<UnauthorizedException> {
            interceptor.preHandle(request, response, handler)
        }
    }

    @Test
    fun `throws UnauthorizedException when authorization header is not a bearer token`() {
        val request = requestWithAuthorization("Token abc")
        val handler = handlerMethodFor(AnnotatedController::class.java, "anyEndpoint")

        assertThrows<UnauthorizedException> {
            interceptor.preHandle(request, response, handler)
        }
    }

    @Test
    fun `throws UnauthorizedException when bearer token is blank`() {
        val request = requestWithAuthorization("Bearer    ")
        val handler = handlerMethodFor(AnnotatedController::class.java, "anyEndpoint")

        assertThrows<UnauthorizedException> {
            interceptor.preHandle(request, response, handler)
        }
    }

    @Test
    fun `allows class-level protected request with valid bearer token`() {
        val request = requestWithAuthorization("Bearer valid-token")
        val handler = handlerMethodFor(AnnotatedController::class.java, "anyEndpoint")
        whenever(adminJwtService.parseToken("valid-token")).thenReturn(
            AdminJwtClaims(accountId = 42L, role = AdminAccountRole.SUPER_ADMIN)
        )

        val result = interceptor.preHandle(request, response, handler)

        assertThat(result).isTrue()
        verify(request).setAttribute(AdminJwtService.ADMIN_ACCOUNT_ID_ATTR, 42L)
        verify(request).setAttribute(AdminJwtService.ADMIN_ACCOUNT_ROLE_ATTR, AdminAccountRole.SUPER_ADMIN)
    }

    @Test
    fun `allows method-level protected request with valid bearer token`() {
        val request = requestWithAuthorization("Bearer valid-token")
        val handler = handlerMethodFor(SampleController::class.java, "protected")
        whenever(adminJwtService.parseToken("valid-token")).thenReturn(
            AdminJwtClaims(accountId = 7L, role = AdminAccountRole.ADMIN)
        )

        val result = interceptor.preHandle(request, response, handler)

        assertThat(result).isTrue()
        verify(request).setAttribute(AdminJwtService.ADMIN_ACCOUNT_ID_ATTR, 7L)
        verify(request).setAttribute(AdminJwtService.ADMIN_ACCOUNT_ROLE_ATTR, AdminAccountRole.ADMIN)
    }

    @Test
    fun `propagates UnauthorizedException when token is invalid`() {
        val request = requestWithAuthorization("Bearer invalid-token")
        val handler = handlerMethodFor(AnnotatedController::class.java, "anyEndpoint")
        whenever(adminJwtService.parseToken("invalid-token")).thenThrow(
            UnauthorizedException("유효하지 않은 인증 토큰입니다.")
        )

        assertThrows<UnauthorizedException> {
            interceptor.preHandle(request, response, handler)
        }
    }

    private fun requestWithAuthorization(value: String): HttpServletRequest {
        val request: HttpServletRequest = mock()
        whenever(request.getHeader("Authorization")).thenReturn(value)
        return request
    }

    private fun handlerMethodFor(beanType: Class<*>, methodName: String): HandlerMethod {
        val bean = beanType.getDeclaredConstructor().newInstance()
        val method = beanType.getDeclaredMethod(methodName)
        return HandlerMethod(bean, method)
    }

    @AdminAuthRequired
    class AnnotatedController {
        fun anyEndpoint() = Unit
    }

    class SampleController {
        fun open() = Unit

        @AdminAuthRequired
        fun protected() = Unit
    }
}
