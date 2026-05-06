package org.happyzion.api.youtube.infrastructure.persistence

import org.happyzion.api.youtube.domain.YouTubePlaylistItem
import org.springframework.data.jpa.repository.JpaRepository

interface YouTubePlaylistItemRepository : JpaRepository<YouTubePlaylistItem, Long> {
    fun findAllByPlaylistIdOrderByPositionAsc(playlistId: Long): List<YouTubePlaylistItem>
    fun findAllByPlaylistIdIn(playlistIds: Collection<Long>): List<YouTubePlaylistItem>
    fun findAllByVideoId(videoId: Long): List<YouTubePlaylistItem>
    fun deleteAllByPlaylistId(playlistId: Long)
}
