package org.happyzion.api.youtube.interfaces.api

import org.happyzion.api.common.security.AdminAuthRequired
import org.happyzion.api.menu.interfaces.dto.AdminYouTubePlaylistsResponse
import org.happyzion.api.menu.interfaces.dto.toDto
import org.happyzion.api.youtube.application.YouTubeSyncService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@AdminAuthRequired
@RestController
@RequestMapping("/api/v1/admin/youtube")
class YouTubeAdminController(
    private val youTubeSyncService: YouTubeSyncService,
) {
    @GetMapping("/playlists")
    fun getPlaylists(): AdminYouTubePlaylistsResponse =
        AdminYouTubePlaylistsResponse(
            playlists = youTubeSyncService.getPlaylistSummaries().map { it.toDto() },
        )

    @PostMapping("/sync")
    fun sync() = youTubeSyncService.sync().toDto()
}
