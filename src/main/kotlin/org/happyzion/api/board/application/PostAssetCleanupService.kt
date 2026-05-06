package org.happyzion.api.board.application

import org.happyzion.api.board.infrastructure.persistence.PostAssetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime

@Service
class PostAssetCleanupService(
    private val postAssetRepository: PostAssetRepository,
    private val attachmentStorage: AttachmentStorage,
    private val clock: Clock,
) {
    @Transactional
    fun cleanupStaleTemporaryAssets(): Long {
        val cutoff = OffsetDateTime.now(clock).minusHours(24)
        val staleAssets = postAssetRepository.findAllByPostIdIsNullAndDetachedAtBefore(cutoff)

        if (staleAssets.isEmpty()) {
            return 0L
        }

        staleAssets.forEach { asset ->
            attachmentStorage.delete(asset.storedPath)
        }
        postAssetRepository.deleteAll(staleAssets)

        return staleAssets.size.toLong()
    }
}
