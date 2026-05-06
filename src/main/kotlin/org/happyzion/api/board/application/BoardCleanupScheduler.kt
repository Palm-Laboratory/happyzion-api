package org.happyzion.api.board.application

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class BoardCleanupScheduler(
    private val uploadTokenCleanupService: UploadTokenCleanupService,
    private val postAssetCleanupService: PostAssetCleanupService,
) {
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    fun cleanupBoardUploadState() {
        uploadTokenCleanupService.cleanupExpiredTokens()
        postAssetCleanupService.cleanupStaleTemporaryAssets()
    }
}
