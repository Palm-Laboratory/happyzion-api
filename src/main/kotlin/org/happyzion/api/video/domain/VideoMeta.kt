package org.happyzion.api.video.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "youtube_video_meta")
class VideoMeta(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "video_id", nullable = false, unique = true)
    val videoId: Long,
    @Column(name = "display_title", length = 300)
    var displayTitle: String? = null,
    @Column(name = "preacher_name", length = 120)
    var preacherName: String? = null,
    @Column(name = "display_published_at")
    var displayPublishedAt: OffsetDateTime? = null,
    @Column(nullable = false)
    var hidden: Boolean = false,
    @Column(name = "scripture_reference", length = 200)
    var scriptureReference: String? = null,
    @Column(name = "scripture_body")
    var scriptureBody: String? = null,
    @Column
    var summary: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
