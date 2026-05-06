package org.happyzion.api.youtube.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "youtube_playlist")
class YouTubePlaylist(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "channel_id", nullable = false)
    val channelId: Long,
    @Column(name = "playlist_id", nullable = false, unique = true, length = 64)
    val playlistId: String,
    @Column(nullable = false, length = 200)
    var title: String,
    @Column
    var description: String? = null,
    @Column(name = "thumbnail_url")
    var thumbnailUrl: String? = null,
    @Column(name = "item_count", nullable = false)
    var itemCount: Int = 0,
    @Column(name = "published_at")
    var publishedAt: OffsetDateTime? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 16)
    var syncStatus: YouTubeSyncStatus = YouTubeSyncStatus.ACTIVE,
    @Column(name = "last_synced_at")
    var lastSyncedAt: OffsetDateTime? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
