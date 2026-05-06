package org.happyzion.api.board.application

import org.happyzion.api.board.infrastructure.persistence.UploadTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime

@Service
class UploadTokenCleanupService(
    private val uploadTokenRepository: UploadTokenRepository,
    private val clock: Clock,
) {
    @Transactional
    fun cleanupExpiredTokens(): Long {
        val cutoff = OffsetDateTime.now(clock).minusHours(24)
        return uploadTokenRepository.deleteByExpiresAtBefore(cutoff)
    }
}
