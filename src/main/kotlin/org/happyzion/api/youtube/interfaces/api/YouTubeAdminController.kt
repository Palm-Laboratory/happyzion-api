package org.happyzion.api.youtube.interfaces.api

import org.happyzion.api.common.config.AdminProperties
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.menu.interfaces.dto.AdminYouTubePlaylistsResponse
import org.happyzion.api.menu.interfaces.dto.toDto
import org.happyzion.api.youtube.application.YouTubeSyncService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/youtube")
class YouTubeAdminController(
    private val youTubeSyncService: YouTubeSyncService,
    private val adminProperties: AdminProperties,
) {
    @GetMapping("/playlists")
    fun getPlaylists(
        @RequestHeader("X-Admin-Key", required = false) adminKey: String?,
    ): AdminYouTubePlaylistsResponse {
        validateAdminKey(adminKey)
        return AdminYouTubePlaylistsResponse(
            playlists = youTubeSyncService.getPlaylistSummaries().map { it.toDto() }
        )
    }

    @PostMapping("/sync")
    fun sync(
        @RequestHeader("X-Admin-Key", required = false) adminKey: String?,
    ) =
        run {
        validateAdminKey(adminKey)
        youTubeSyncService.sync().toDto()
        }

    private fun validateAdminKey(adminKey: String?) {
        val configuredKey = adminProperties.syncKey.trim()
        if (configuredKey.isBlank()) {
            throw IllegalStateException("ADMIN_SYNC_KEY is not configured.")
        }

        if (adminKey.isNullOrBlank() || adminKey != configuredKey) {
            throw ForbiddenException("관리자 키가 올바르지 않습니다.")
        }
    }
}
