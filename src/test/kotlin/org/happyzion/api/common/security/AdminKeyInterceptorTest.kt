package org.happyzion.api.common.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.happyzion.api.common.config.AdminProperties
import org.happyzion.api.common.error.ForbiddenException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.method.HandlerMethod

class AdminKeyInterceptorTest {

    private val interceptor = AdminKeyInterceptor(AdminProperties(syncKey = "secret-key"))
    private val response: HttpServletResponse = mock()

    @Test
    fun `passes through when handler is not a HandlerMethod`() {
        val request: HttpServletRequest = mock()

        val result = interceptor.preHandle(request, response, Any())

        assertThat(result).isTrue()
    }

    @Test
    fun `passes through when handler has no AdminKeyRequired annotation`() {
        val request = requestWithAdminKey(null)
        val handler = handlerMethodFor(SampleController::class.java, "open")

        val result = interceptor.preHandle(request, response, handler)

        assertThat(result).isTrue()
    }

    @Test
    fun `allows request when class-level annotation is present and admin key matches`() {
        val request = requestWithAdminKey("secret-key")
        val handler = handlerMethodFor(AnnotatedController::class.java, "anyEndpoint")

        val result = interceptor.preHandle(request, response, handler)

        assertThat(result).isTrue()
    }

    @Test
    fun `allows request when method-level annotation is present and admin key matches`() {
        val request = requestWithAdminKey("secret-key")
        val handler = handlerMethodFor(SampleController::class.java, "protected")

        val result = interceptor.preHandle(request, response, handler)

        assertThat(result).isTrue()
    }

    @Test
    fun `throws ForbiddenException when admin key is missing`() {
        val request = requestWithAdminKey(null)
        val handler = handlerMethodFor(AnnotatedController::class.java, "anyEndpoint")

        assertThrows<ForbiddenException> {
            interceptor.preHandle(request, response, handler)
        }
    }

    @Test
    fun `throws ForbiddenException when admin key is blank`() {
        val request = requestWithAdminKey("   ")
        val handler = handlerMethodFor(AnnotatedController::class.java, "anyEndpoint")

        assertThrows<ForbiddenException> {
            interceptor.preHandle(request, response, handler)
        }
    }

    @Test
    fun `throws ForbiddenException when admin key does not match`() {
        val request = requestWithAdminKey("wrong-key")
        val handler = handlerMethodFor(AnnotatedController::class.java, "anyEndpoint")

        assertThrows<ForbiddenException> {
            interceptor.preHandle(request, response, handler)
        }
    }

    @Test
    fun `throws IllegalStateException when configured sync key is blank`() {
        val blankInterceptor = AdminKeyInterceptor(AdminProperties(syncKey = "   "))
        val request = requestWithAdminKey("anything")
        val handler = handlerMethodFor(AnnotatedController::class.java, "anyEndpoint")

        assertThrows<IllegalStateException> {
            blankInterceptor.preHandle(request, response, handler)
        }
    }

    private fun requestWithAdminKey(value: String?): HttpServletRequest {
        val request: HttpServletRequest = mock()
        whenever(request.getHeader(AdminKeyInterceptor.HEADER_NAME)).thenReturn(value)
        return request
    }

    private fun handlerMethodFor(beanType: Class<*>, methodName: String): HandlerMethod {
        val bean = beanType.getDeclaredConstructor().newInstance()
        val method = beanType.getDeclaredMethod(methodName)
        return HandlerMethod(bean, method)
    }

    @AdminKeyRequired
    class AnnotatedController {
        fun anyEndpoint() = Unit
    }

    class SampleController {
        fun open() = Unit

        @AdminKeyRequired
        fun protected() = Unit
    }
}
