package org.happyzion.api.board.infrastructure.persistence

import org.happyzion.api.board.domain.UploadToken
import org.springframework.data.jpa.repository.JpaRepository

interface UploadTokenRepository : JpaRepository<UploadToken, Long> {
    fun findByTokenHash(tokenHash: String): UploadToken?

    fun deleteByExpiresAtBefore(cutoff: java.time.OffsetDateTime): Long
}
