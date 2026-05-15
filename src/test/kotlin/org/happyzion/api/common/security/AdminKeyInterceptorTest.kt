package org.happyzion.api.common.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.happyzion.api.common.config.AdminProperties
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.UnauthorizedException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.method.HandlerMethod
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class AdminKeyInterceptorTest {

    private val interceptor = AdminKeyInterceptor(AdminProperties(syncKey = "secret-key", actorSigningSecret = "actor-secret"))
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
        val blankInterceptor = AdminKeyInterceptor(AdminProperties(syncKey = "   ", actorSigningSecret = "actor-secret"))
        val request = requestWithAdminKey("anything")
        val handler = handlerMethodFor(AnnotatedController::class.java, "anyEndpoint")

        assertThrows<IllegalStateException> {
            blankInterceptor.preHandle(request, response, handler)
        }
    }

    @Test
    fun `throws IllegalStateException when actor signing secret is blank`() {
        val noSecretInterceptor = AdminKeyInterceptor(AdminProperties(syncKey = "secret-key", actorSigningSecret = "   "))
        val ts = currentTimestamp()
        val request = requestWithActorHeaders("42", ts, "any-sig")
        val handler = handlerMethodFor(AnnotatedController::class.java, "anyEndpoint")

        assertThrows<IllegalStateException> {
            noSecretInterceptor.preHandle(request, response, handler)
        }
    }

    @Test
    fun `passes through when actor id header is absent`() {
        val request = requestWithAdminKey("secret-key")
        val handler = handlerMethodFor(AnnotatedController::class.java, "anyEndpoint")

        val result = interceptor.preHandle(request, response, handler)

        assertThat(result).isTrue()
    }

    @Test
    fun `throws UnauthorizedException when actor id is present but timestamp header is missing`() {
        val request = requestWithAdminKey("secret-key")
        whenever(request.getHeader(AdminKeyInterceptor.ACTOR_ID_HEADER)).thenReturn("42")
        val handler = handlerMethodFor(AnnotatedController::class.java, "anyEndpoint")

        assertThrows<UnauthorizedException> {
            interceptor.preHandle(request, response, handler)
        }
    }

    @Test
    fun `throws UnauthorizedException when actor sig header is missing`() {
        val ts = currentTimestamp()
        val request = requestWithAdminKey("secret-key")
        whenever(request.getHeader(AdminKeyInterceptor.ACTOR_ID_HEADER)).thenReturn("42")
        whenever(request.getHeader(AdminKeyInterceptor.ACTOR_TIMESTAMP_HEADER)).thenReturn(ts.toString())
        val handler = handlerMethodFor(AnnotatedController::class.java, "anyEndpoint")

        assertThrows<UnauthorizedException> {
            interceptor.preHandle(request, response, handler)
        }
    }

    @Test
    fun `throws UnauthorizedException when actor timestamp is outside the 5-minute window`() {
        val staleTs = currentTimestamp() - 301
        val sig = computeTestHmac("42", staleTs)
        val request = requestWithActorHeaders("42", staleTs, sig)
        val handler = handlerMethodFor(AnnotatedController::class.java, "anyEndpoint")

        assertThrows<UnauthorizedException> {
            interceptor.preHandle(request, response, handler)
        }
    }

    @Test
    fun `throws UnauthorizedException when actor sig does not match`() {
        val ts = currentTimestamp()
        val request = requestWithActorHeaders("42", ts, "badsig")
        val handler = handlerMethodFor(AnnotatedController::class.java, "anyEndpoint")

        assertThrows<UnauthorizedException> {
            interceptor.preHandle(request, response, handler)
        }
    }

    @Test
    fun `allows request when actor id, timestamp, and correct sig are provided`() {
        val ts = currentTimestamp()
        val sig = computeTestHmac("42", ts)
        val request = requestWithActorHeaders("42", ts, sig)
        val handler = handlerMethodFor(AnnotatedController::class.java, "anyEndpoint")

        val result = interceptor.preHandle(request, response, handler)

        assertThat(result).isTrue()
    }

    private fun requestWithAdminKey(value: String?): HttpServletRequest {
        val request: HttpServletRequest = mock()
        whenever(request.getHeader(AdminKeyInterceptor.HEADER_NAME)).thenReturn(value)
        return request
    }

    private fun requestWithActorHeaders(actorId: String, timestamp: Long, sig: String?): HttpServletRequest {
        val request = requestWithAdminKey("secret-key")
        whenever(request.getHeader(AdminKeyInterceptor.ACTOR_ID_HEADER)).thenReturn(actorId)
        whenever(request.getHeader(AdminKeyInterceptor.ACTOR_TIMESTAMP_HEADER)).thenReturn(timestamp.toString())
        whenever(request.getHeader(AdminKeyInterceptor.ACTOR_SIG_HEADER)).thenReturn(sig)
        return request
    }

    private fun computeTestHmac(actorId: String, timestamp: Long): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("actor-secret".toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal("$actorId:$timestamp".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun currentTimestamp() = System.currentTimeMillis() / 1000

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
