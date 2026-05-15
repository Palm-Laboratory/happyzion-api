package org.happyzion.api.common.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.happyzion.api.common.config.AdminProperties
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.UnauthorizedException
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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
        validateActorSig(request)
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

    private fun validateActorSig(request: HttpServletRequest) {
        val actorId = request.getHeader(ACTOR_ID_HEADER) ?: return

        val signingSecret = adminProperties.actorSigningSecret.trim()
        if (signingSecret.isBlank()) {
            throw IllegalStateException("ADMIN_ACTOR_SIGNING_SECRET is not configured.")
        }

        val timestamp = request.getHeader(ACTOR_TIMESTAMP_HEADER)
            ?: throw UnauthorizedException("actor 서명 헤더가 없습니다.")
        val sig = request.getHeader(ACTOR_SIG_HEADER)
            ?: throw UnauthorizedException("actor 서명 헤더가 없습니다.")

        val tsSeconds = timestamp.toLongOrNull()
            ?: throw UnauthorizedException("actor 서명이 올바르지 않습니다.")

        val nowSeconds = System.currentTimeMillis() / 1000
        if (kotlin.math.abs(nowSeconds - tsSeconds) > TIMESTAMP_WINDOW_SECONDS) {
            throw UnauthorizedException("actor 서명이 만료되었습니다.")
        }

        val expected = computeHmacSha256("$actorId:$timestamp", signingSecret)
        if (!MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), sig.toByteArray(Charsets.UTF_8))) {
            throw UnauthorizedException("actor 서명이 올바르지 않습니다.")
        }
    }

    private fun computeHmacSha256(message: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val HEADER_NAME = "X-Admin-Key"
        const val ACTOR_ID_HEADER = "X-Admin-Actor-Id"
        const val ACTOR_TIMESTAMP_HEADER = "X-Admin-Actor-Timestamp"
        const val ACTOR_SIG_HEADER = "X-Admin-Actor-Sig"
        private const val TIMESTAMP_WINDOW_SECONDS = 300L
    }
}
