package org.happyzion.api.youtube.application

import org.happyzion.api.youtube.domain.YouTubePrivacyStatus
import org.happyzion.api.youtube.domain.YouTubeSyncStatus
import org.happyzion.api.youtube.domain.YouTubeVideo
import org.happyzion.api.youtube.infrastructure.persistence.YouTubePlaylistItemRepository
import org.happyzion.api.youtube.infrastructure.persistence.YouTubeVideoRepository
import org.springframework.stereotype.Component

@Component
class PlaylistDisplayableVideoCountResolver(
    private val youTubePlaylistItemRepository: YouTubePlaylistItemRepository,
    private val youTubeVideoRepository: YouTubeVideoRepository,
) {
    fun resolve(playlistId: Long): Int =
        resolveAll(listOf(playlistId))[playlistId] ?: 0

    fun resolveAll(playlistIds: Collection<Long>): Map<Long, Int> {
        if (playlistIds.isEmpty()) {
            return emptyMap()
        }

        val playlistItems = youTubePlaylistItemRepository.findAllByPlaylistIdIn(playlistIds)
        if (playlistItems.isEmpty()) {
            return playlistIds.associateWith { 0 }
        }

        val videosById = youTubeVideoRepository.findAllById(playlistItems.map { it.videoId }.distinct())
            .associateBy { it.id!! }

        val counts = playlistIds.associateWith { 0 }.toMutableMap()
        playlistItems.forEach { playlistItem ->
            val video = videosById[playlistItem.videoId] ?: return@forEach
            if (isDisplayable(video)) {
                counts[playlistItem.playlistId] = (counts[playlistItem.playlistId] ?: 0) + 1
            }
        }

        return counts
    }

    private fun isDisplayable(video: YouTubeVideo): Boolean =
        video.syncStatus == YouTubeSyncStatus.ACTIVE &&
            video.privacyStatus != YouTubePrivacyStatus.PRIVATE
}
