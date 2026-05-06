package org.happyzion.api.board.application

import org.happyzion.api.board.infrastructure.persistence.UploadTokenRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset

class UploadTokenCleanupServiceTest {
    private val uploadTokenRepository: UploadTokenRepository = mock()
    private val fixedNow = OffsetDateTime.parse("2026-04-20T09:00:00Z")
    private val clock: Clock = Clock.fixed(fixedNow.toInstant(), ZoneOffset.UTC)
    private val service = UploadTokenCleanupService(
        uploadTokenRepository = uploadTokenRepository,
        clock = clock,
    )

    @Test
    fun `cleanupExpiredTokens deletes tokens expired more than 24 hours ago and returns deleted count`() {
        val cutoff: OffsetDateTime = fixedNow.minusHours(24)
        whenever(uploadTokenRepository.deleteByExpiresAtBefore(cutoff)).thenReturn(3L)

        val deletedCount: Long = service.cleanupExpiredTokens()

        verify(uploadTokenRepository).deleteByExpiresAtBefore(cutoff)
        assertThat(deletedCount).isEqualTo(3L)
        assertThat(cutoff).isEqualTo(fixedNow.minusHours(24))
    }
}
