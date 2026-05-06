package org.happyzion.api.youtube.infrastructure.persistence

import org.happyzion.api.youtube.domain.YouTubeVideo
import org.springframework.data.jpa.repository.JpaRepository

interface YouTubeVideoRepository : JpaRepository<YouTubeVideo, Long> {
    fun findByVideoId(videoId: String): YouTubeVideo?
    fun findAllByChannelIdOrderByPublishedAtDesc(channelId: Long): List<YouTubeVideo>
    fun findAllByChannelId(channelId: Long): List<YouTubeVideo>
}
