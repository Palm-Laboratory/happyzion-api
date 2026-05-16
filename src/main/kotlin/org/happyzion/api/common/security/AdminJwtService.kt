package org.happyzion.api.common.security

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.happyzion.api.adminaccount.domain.AdminAccountRole
import org.happyzion.api.common.config.AdminProperties
import org.happyzion.api.common.error.UnauthorizedException
import org.springframework.stereotype.Service
import java.util.Date
import javax.crypto.SecretKey

@Service
class AdminJwtService(private val adminProperties: AdminProperties) {

    private val key: SecretKey by lazy {
        val secret = adminProperties.jwtSecret.trim()
        if (secret.isBlank()) throw IllegalStateException("ADMIN_JWT_SECRET is not configured.")
        Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))
    }

    fun issueToken(accountId: Long, role: AdminAccountRole): String {
        val now = Date()
        return Jwts.builder()
            .subject(accountId.toString())
            .claim(CLAIM_ROLE, role.name)
            .issuedAt(now)
            .expiration(Date(now.time + TTL_MILLIS))
            .signWith(key)
            .compact()
    }

    fun parseToken(bearerToken: String): AdminJwtClaims {
        val claims = try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(bearerToken)
                .payload
        } catch (e: JwtException) {
            throw UnauthorizedException("유효하지 않은 인증 토큰입니다.")
        } catch (e: IllegalArgumentException) {
            throw UnauthorizedException("유효하지 않은 인증 토큰입니다.")
        }

        val accountId = claims.subject?.toLongOrNull()
            ?: throw UnauthorizedException("유효하지 않은 인증 토큰입니다.")
        val roleStr = claims.get(CLAIM_ROLE, String::class.java)
            ?: throw UnauthorizedException("유효하지 않은 인증 토큰입니다.")
        val role = runCatching { AdminAccountRole.valueOf(roleStr) }.getOrElse {
            throw UnauthorizedException("유효하지 않은 인증 토큰입니다.")
        }

        return AdminJwtClaims(accountId = accountId, role = role)
    }

    companion object {
        const val ADMIN_ACCOUNT_ID_ATTR = "adminAccountId"
        const val ADMIN_ACCOUNT_ROLE_ATTR = "adminAccountRole"
        private const val CLAIM_ROLE = "role"
        private const val TTL_MILLIS = 8 * 60 * 60 * 1000L
    }
}

data class AdminJwtClaims(val accountId: Long, val role: AdminAccountRole)
