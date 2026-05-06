package org.happyzion.api.youtube.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "youtube_playlist_item")
class YouTubePlaylistItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "playlist_id", nullable = false)
    val playlistId: Long,
    @Column(name = "video_id", nullable = false)
    val videoId: Long,
    @Column(nullable = false)
    var position: Int,
    @Column(name = "added_at")
    var addedAt: OffsetDateTime? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
