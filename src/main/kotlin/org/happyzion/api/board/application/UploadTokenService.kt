package org.happyzion.api.board.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.happyzion.api.board.domain.PostAssetKind
import org.happyzion.api.board.domain.UploadToken
import org.happyzion.api.board.infrastructure.persistence.UploadTokenRepository
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.UnauthorizedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.OffsetDateTime
import java.util.Base64

@Service
class UploadTokenService(
    private val uploadTokenRepository: UploadTokenRepository,
    private val clock: Clock,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) {
    private val secureRandom = SecureRandom()

    @Transactional
    fun issueToken(
        actorId: Long,
        kind: PostAssetKind,
        maxByteSize: Long,
        allowedMimeTypes: List<String>,
    ): UploadTokenIssueResult {
        val rawToken = generateRawToken()
        val token = UploadToken(
            actorId = actorId,
            maxByteSize = maxByteSize,
            tokenHash = sha256Hex(rawToken),
            assetKind = kind,
            allowedMimeTypes = objectMapper.writeValueAsString(allowedMimeTypes),
            expiresAt = OffsetDateTime.now(clock).plusMinutes(5),
        )

        uploadTokenRepository.save(token)

        return UploadTokenIssueResult(rawToken = rawToken)
    }

    @Transactional
    fun validateAndConsume(
        rawToken: String,
        kind: PostAssetKind,
        byteSize: Long,
        mimeType: String,
    ): UploadTokenValidationResult {
        val token = uploadTokenRepository.findByTokenHash(sha256Hex(rawToken))
            ?: throw UnauthorizedException(GENERIC_TOKEN_FAILURE_MESSAGE)

        val now = OffsetDateTime.now(clock)
        if (token.expiresAt.isBefore(now) || token.usedAt != null) {
            throw UnauthorizedException(GENERIC_TOKEN_FAILURE_MESSAGE)
        }

        val tokenAllowedMimeTypes = allowedMimeTypes(token)
        if (token.assetKind != kind || byteSize > token.maxByteSize || !tokenAllowedMimeTypes.contains(mimeType)) {
            throw ForbiddenException(GENERIC_TOKEN_FAILURE_MESSAGE)
        }

        token.usedAt = now
        uploadTokenRepository.save(token)

        return UploadTokenValidationResult(
            actorId = token.actorId,
            kind = token.assetKind,
            maxByteSize = token.maxByteSize,
            allowedMimeTypes = tokenAllowedMimeTypes,
        )
    }

    private fun generateRawToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun allowedMimeTypes(token: UploadToken): List<String> =
        objectMapper.readValue(token.allowedMimeTypes, objectMapper.typeFactory.constructCollectionType(List::class.java, String::class.java))

    companion object {
        private const val GENERIC_TOKEN_FAILURE_MESSAGE = "유효하지 않은 업로드 토큰입니다."
    }
}

data class UploadTokenIssueResult(
    val rawToken: String,
)

data class UploadTokenValidationResult(
    val actorId: Long,
    val kind: PostAssetKind,
    val maxByteSize: Long,
    val allowedMimeTypes: List<String>,
)
