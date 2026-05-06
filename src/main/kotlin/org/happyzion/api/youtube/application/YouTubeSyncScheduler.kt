package org.happyzion.api.youtube.application

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class YouTubeSyncScheduler(
    private val youTubeSyncService: YouTubeSyncService,
) {
    @Scheduled(cron = "0 0 8,23 * * *", zone = "Asia/Seoul")
    fun scheduledSync() {
        if (!youTubeSyncService.isConfigured()) {
            return
        }

        youTubeSyncService.sync()
    }
}
