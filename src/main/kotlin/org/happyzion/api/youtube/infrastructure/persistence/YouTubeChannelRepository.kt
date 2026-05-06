package org.happyzion.api.youtube.infrastructure.persistence

import org.happyzion.api.youtube.domain.YouTubeChannel
import org.springframework.data.jpa.repository.JpaRepository

interface YouTubeChannelRepository : JpaRepository<YouTubeChannel, Long> {
    fun findByChannelId(channelId: String): YouTubeChannel?
}
