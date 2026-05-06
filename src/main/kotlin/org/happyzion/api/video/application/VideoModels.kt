package org.happyzion.api.video.application

import org.happyzion.api.youtube.domain.YouTubeContentForm
import java.time.OffsetDateTime

data class PublicVideoSummary(
    val videoId: String,
    val title: String,
    val preacherName: String?,
    val publishedAt: OffsetDateTime?,
    val thumbnailUrl: String?,
    val scriptureReference: String?,
    val summary: String?,
    val contentForm: YouTubeContentForm,
    val href: String,
)

data class PublicVideoList(
    val form: YouTubeContentForm,
    val featured: PublicVideoSummary?,
    val items: List<PublicVideoSummary>,
    val currentPage: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int,
)

data class PublicVideoPlaylistLink(
    val label: String,
    val href: String,
)

data class PublicShortformPlaylistWindow(
    val items: List<PublicVideoSummary>,
    val currentIndexInWindow: Int,
    val currentPage: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int,
)

data class PublicVideoDetail(
    val videoId: String,
    val title: String,
    val sourceTitle: String,
    val preacherName: String?,
    val publishedAt: OffsetDateTime?,
    val thumbnailUrl: String?,
    val scriptureReference: String?,
    val scriptureBody: String?,
    val messageBody: String?,
    val summary: String?,
    val description: String?,
    val contentForm: YouTubeContentForm,
    val playlists: List<PublicVideoPlaylistLink>,
    val related: List<PublicVideoSummary>,
    val shortformPlaylist: PublicShortformPlaylistWindow?,
)

data class AdminVideoSummary(
    val videoId: String,
    val title: String,
    val sourceTitle: String,
    val preacherName: String?,
    val publishedAt: OffsetDateTime?,
    val hidden: Boolean,
    val contentForm: YouTubeContentForm,
    val thumbnailUrl: String?,
    val scriptureReference: String?,
)

data class AdminVideoDetail(
    val videoId: String,
    val sourceTitle: String,
    val sourceDescription: String?,
    val sourcePublishedAt: OffsetDateTime?,
    val sourceThumbnailUrl: String?,
    val title: String,
    val preacherName: String?,
    val publishedAt: OffsetDateTime?,
    val hidden: Boolean,
    val scriptureReference: String?,
    val scriptureBody: String?,
    val messageBody: String?,
    val summary: String?,
    val thumbnailOverrideUrl: String?,
    val contentForm: YouTubeContentForm,
    val publicHref: String?,
)

data class UpdateVideoMetaCommand(
    val displayTitle: String?,
    val preacherName: String?,
    val displayPublishedAt: OffsetDateTime?,
    val hidden: Boolean,
    val scriptureReference: String?,
    val scriptureBody: String?,
    val messageBody: String?,
    val summary: String?,
    val thumbnailOverrideUrl: String?,
)
