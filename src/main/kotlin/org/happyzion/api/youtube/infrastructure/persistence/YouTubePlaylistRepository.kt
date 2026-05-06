package org.happyzion.api.youtube.infrastructure.persistence

import org.happyzion.api.youtube.domain.YouTubePlaylist
import org.springframework.data.jpa.repository.JpaRepository

interface YouTubePlaylistRepository : JpaRepository<YouTubePlaylist, Long> {
    fun findByPlaylistId(playlistId: String): YouTubePlaylist?
    fun findAllByChannelId(channelId: Long): List<YouTubePlaylist>
}
