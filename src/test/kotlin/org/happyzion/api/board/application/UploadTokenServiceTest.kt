package org.happyzion.api.board.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.happyzion.api.board.domain.PostAssetKind
import org.happyzion.api.board.domain.UploadToken
import org.happyzion.api.board.infrastructure.persistence.UploadTokenRepository
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.UnauthorizedException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset

class UploadTokenServiceTest {
    private val uploadTokenRepository: UploadTokenRepository = mock()
    private val fixedNow = OffsetDateTime.parse("2026-04-20T09:00:00Z")
    private val clock: Clock = Clock.fixed(fixedNow.toInstant(), ZoneOffset.UTC)
    private val service = UploadTokenService(
        uploadTokenRepository = uploadTokenRepository,
        clock = clock,
    )

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `issueToken stores only a hashed scoped token and returns the raw token`() {
        whenever(uploadTokenRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<UploadToken>(0)
        }

        val result = service.issueToken(
            actorId = 17L,
            kind = PostAssetKind.INLINE_IMAGE,
            maxByteSize = 2_048_000L,
            allowedMimeTypes = listOf("image/png", "image/jpeg"),
        )

        val rawToken = readStringProperty(result, "rawToken")
        val savedToken = argumentCaptor<UploadToken>().apply {
            verify(uploadTokenRepository).save(capture())
        }.firstValue

        assertThat(rawToken).isNotBlank
        assertThat(savedToken.tokenHash).isNotEqualTo(rawToken)
        assertThat(savedToken.tokenHash).matches("[0-9a-f]{64}")
        assertThat(savedToken.actorId).isEqualTo(17L)
        assertThat(savedToken.assetKind).isEqualTo(PostAssetKind.INLINE_IMAGE)
        assertThat(savedToken.maxByteSize).isEqualTo(2_048_000L)
        assertThat(savedToken.usedAt).isNull()
        assertThat(savedToken.expiresAt).isEqualTo(fixedNow.plusMinutes(5))
        assertThat(objectMapper.readValue<List<String>>(savedToken.allowedMimeTypes))
            .containsExactly("image/png", "image/jpeg")
    }

    @Test
    fun `validateAndConsume accepts an in-scope token and records usedAt`() {
        val rawToken = "raw-upload-token-value"
        val tokenHash = sha256Hex(rawToken)
        val storedToken = token(
            actorId = 17L,
            kind = PostAssetKind.FILE_ATTACHMENT,
            maxByteSize = 3_000_000L,
            allowedMimeTypes = listOf("application/pdf", "image/png"),
            tokenHash = tokenHash,
            expiresAt = fixedNow.plusMinutes(3),
        )
        whenever(uploadTokenRepository.findByTokenHash(tokenHash)).thenReturn(storedToken)
        whenever(uploadTokenRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<UploadToken>(0)
        }

        val result = service.validateAndConsume(
            rawToken = rawToken,
            kind = PostAssetKind.FILE_ATTACHMENT,
            byteSize = 2_000_000L,
            mimeType = "application/pdf",
        )

        val savedToken = argumentCaptor<UploadToken>().apply {
            verify(uploadTokenRepository).save(capture())
        }.firstValue

        assertThat(readLongProperty(result, "actorId")).isEqualTo(17L)
        assertThat(readEnumProperty(result, "kind")).isEqualTo(PostAssetKind.FILE_ATTACHMENT)
        assertThat(readLongProperty(result, "maxByteSize")).isEqualTo(3_000_000L)
        assertThat(readCollectionProperty(result, "allowedMimeTypes"))
            .containsExactly("application/pdf", "image/png")
        assertThat(savedToken.usedAt).isEqualTo(fixedNow)
    }

    @Test
    fun `validateAndConsume rejects expired token without consuming it`() {
        assertRejectedWithoutConsumption(
            rawToken = "expired-token",
            token = token(
                actorId = 21L,
                kind = PostAssetKind.INLINE_IMAGE,
                maxByteSize = 1_000_000L,
                allowedMimeTypes = listOf("image/png"),
                tokenHash = sha256Hex("expired-token"),
                expiresAt = fixedNow.minusSeconds(1),
            ),
            requestedKind = PostAssetKind.INLINE_IMAGE,
            byteSize = 1_000L,
            mimeType = "image/png",
            expectedExceptionClass = UnauthorizedException::class.java,
        )
    }

    @Test
    fun `validateAndConsume rejects already used token without consuming it again`() {
        assertRejectedWithoutConsumption(
            rawToken = "used-token",
            token = token(
                actorId = 21L,
                kind = PostAssetKind.FILE_ATTACHMENT,
                maxByteSize = 1_000_000L,
                allowedMimeTypes = listOf("application/pdf"),
                tokenHash = sha256Hex("used-token"),
                expiresAt = fixedNow.plusMinutes(2),
                usedAt = fixedNow.minusMinutes(1),
            ),
            requestedKind = PostAssetKind.FILE_ATTACHMENT,
            byteSize = 1_000L,
            mimeType = "application/pdf",
            expectedExceptionClass = UnauthorizedException::class.java,
        )
    }

    @Test
    fun `validateAndConsume rejects mismatched kind without consuming it`() {
        assertRejectedWithoutConsumption(
            rawToken = "wrong-kind-token",
            token = token(
                actorId = 21L,
                kind = PostAssetKind.INLINE_IMAGE,
                maxByteSize = 1_000_000L,
                allowedMimeTypes = listOf("image/png"),
                tokenHash = sha256Hex("wrong-kind-token"),
                expiresAt = fixedNow.plusMinutes(2),
            ),
            requestedKind = PostAssetKind.FILE_ATTACHMENT,
            byteSize = 1_000L,
            mimeType = "image/png",
            expectedExceptionClass = ForbiddenException::class.java,
        )
    }

    @Test
    fun `validateAndConsume rejects oversized file without consuming it`() {
        assertRejectedWithoutConsumption(
            rawToken = "oversized-token",
            token = token(
                actorId = 21L,
                kind = PostAssetKind.FILE_ATTACHMENT,
                maxByteSize = 1_000_000L,
                allowedMimeTypes = listOf("application/pdf"),
                tokenHash = sha256Hex("oversized-token"),
                expiresAt = fixedNow.plusMinutes(2),
            ),
            requestedKind = PostAssetKind.FILE_ATTACHMENT,
            byteSize = 1_000_001L,
            mimeType = "application/pdf",
            expectedExceptionClass = ForbiddenException::class.java,
        )
    }

    @Test
    fun `validateAndConsume rejects disallowed mime without consuming it`() {
        assertRejectedWithoutConsumption(
            rawToken = "mime-token",
            token = token(
                actorId = 21L,
                kind = PostAssetKind.INLINE_IMAGE,
                maxByteSize = 1_000_000L,
                allowedMimeTypes = listOf("image/png"),
                tokenHash = sha256Hex("mime-token"),
                expiresAt = fixedNow.plusMinutes(2),
            ),
            requestedKind = PostAssetKind.INLINE_IMAGE,
            byteSize = 1_000L,
            mimeType = "image/webp",
            expectedExceptionClass = ForbiddenException::class.java,
        )
    }

    @Test
    fun `issueToken and validateAndConsume do not expose IllegalArgumentException for token failures`() {
        val token = token(
            actorId = 21L,
            kind = PostAssetKind.INLINE_IMAGE,
            maxByteSize = 1_000_000L,
            allowedMimeTypes = listOf("image/png"),
            tokenHash = sha256Hex("failure-token"),
            expiresAt = fixedNow.minusMinutes(1),
        )
        whenever(uploadTokenRepository.findByTokenHash(token.tokenHash)).thenReturn(token)

        assertThrows<UnauthorizedException> {
            service.validateAndConsume(
                rawToken = "failure-token",
                kind = PostAssetKind.INLINE_IMAGE,
                byteSize = 1_000L,
                mimeType = "image/png",
            )
        }

        verify(uploadTokenRepository, never()).save(any())
        assertThat(token.usedAt).isNull()
    }

    private fun assertRejectedWithoutConsumption(
        rawToken: String,
        token: UploadToken,
        requestedKind: PostAssetKind,
        byteSize: Long,
        mimeType: String,
        expectedExceptionClass: Class<out RuntimeException>,
    ) {
        val initialUsedAt = token.usedAt
        whenever(uploadTokenRepository.findByTokenHash(token.tokenHash)).thenReturn(token)

        assertThatThrownBy {
            service.validateAndConsume(
                rawToken = rawToken,
                kind = requestedKind,
                byteSize = byteSize,
                mimeType = mimeType,
            )
        }.isInstanceOf(expectedExceptionClass)

        verify(uploadTokenRepository, never()).save(any())
        assertThat(token.usedAt).isEqualTo(initialUsedAt)
    }

    private fun token(
        actorId: Long,
        kind: PostAssetKind,
        maxByteSize: Long,
        allowedMimeTypes: List<String>,
        tokenHash: String,
        expiresAt: OffsetDateTime,
        usedAt: OffsetDateTime? = null,
    ): UploadToken =
        UploadToken(
            actorId = actorId,
            maxByteSize = maxByteSize,
            tokenHash = tokenHash,
            assetKind = kind,
            allowedMimeTypes = objectMapper.writeValueAsString(allowedMimeTypes),
            expiresAt = expiresAt,
            usedAt = usedAt,
        )

    private fun readStringProperty(target: Any, propertyName: String): String =
        readProperty(target, propertyName) as String

    private fun readLongProperty(target: Any, propertyName: String): Long =
        when (val value = readProperty(target, propertyName)) {
            is Long -> value
            is Int -> value.toLong()
            else -> error("Expected $propertyName to be a number but was ${value?.javaClass}")
        }

    private fun readEnumProperty(target: Any, propertyName: String): PostAssetKind =
        readProperty(target, propertyName) as PostAssetKind

    private fun readCollectionProperty(target: Any, propertyName: String): List<String> =
        when (val value = readProperty(target, propertyName)) {
            is Collection<*> -> value.filterIsInstance<String>()
            else -> error("Expected $propertyName to be a collection but was ${value?.javaClass}")
        }

    private fun readProperty(target: Any, propertyName: String): Any? {
        val getterName = "get" + propertyName.replaceFirstChar { it.uppercaseChar() }
        val getter = target.javaClass.methods.firstOrNull { it.name == getterName && it.parameterCount == 0 }
            ?: error("Missing getter $getterName on ${target.javaClass.name}")
        return getter.invoke(target)
    }

    private fun sha256Hex(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
