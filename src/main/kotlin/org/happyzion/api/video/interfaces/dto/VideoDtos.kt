package org.happyzion.api.video.interfaces.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.happyzion.api.video.application.AdminVideoDetail
import org.happyzion.api.video.application.AdminVideoSummary
import org.happyzion.api.video.application.PublicVideoDetail
import org.happyzion.api.video.application.PublicVideoList
import org.happyzion.api.video.application.PublicVideoPlaylistLink
import org.happyzion.api.video.application.PublicShortformPlaylistWindow
import org.happyzion.api.video.application.PublicVideoSummary
import org.happyzion.api.video.application.UpdateVideoMetaCommand
import org.happyzion.api.youtube.domain.YouTubeContentForm
import java.time.OffsetDateTime

@Schema(name = "VideoPlaylistItemSummary")
data class PublicVideoSummaryDto(
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

@Schema(name = "VideoPlaylistItemsResponse")
data class PublicVideoListResponse(
    val form: YouTubeContentForm,
    val featured: PublicVideoSummaryDto?,
    val items: List<PublicVideoSummaryDto>,
    val currentPage: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int,
)

@Schema(name = "VideoPlaylistLink")
data class PublicVideoPlaylistLinkDto(
    val label: String,
    val href: String,
)

@Schema(name = "VideoShortformPlaylistWindow")
data class PublicShortformPlaylistWindowDto(
    val items: List<PublicVideoSummaryDto>,
    val currentIndexInWindow: Int,
    val currentPage: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int,
)

@Schema(name = "VideoPlaylistItemDetailResponse")
data class PublicVideoDetailResponse(
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
    val playlists: List<PublicVideoPlaylistLinkDto>,
    val related: List<PublicVideoSummaryDto>,
    val shortformPlaylist: PublicShortformPlaylistWindowDto?,
)

data class AdminVideoSummaryDto(
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

data class AdminVideoListResponse(
    val items: List<AdminVideoSummaryDto>,
)

data class AdminVideoDetailResponse(
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

data class UpdateVideoMetaRequest(
    val displayTitle: String? = null,
    val preacherName: String? = null,
    val displayPublishedAt: OffsetDateTime? = null,
    val hidden: Boolean = false,
    val scriptureReference: String? = null,
    val scriptureBody: String? = null,
    val messageBody: String? = null,
    val summary: String? = null,
    val thumbnailOverrideUrl: String? = null,
)

fun PublicVideoSummary.toDto(): PublicVideoSummaryDto =
    PublicVideoSummaryDto(
        videoId = videoId,
        title = title,
        preacherName = preacherName,
        publishedAt = publishedAt,
        thumbnailUrl = thumbnailUrl,
        scriptureReference = scriptureReference,
        summary = summary,
        contentForm = contentForm,
        href = href,
    )

fun PublicVideoList.toDto(): PublicVideoListResponse =
    PublicVideoListResponse(
        form = form,
        featured = featured?.toDto(),
        items = items.map { it.toDto() },
        currentPage = currentPage,
        pageSize = pageSize,
        totalItems = totalItems,
        totalPages = totalPages,
    )

fun PublicVideoPlaylistLink.toDto(): PublicVideoPlaylistLinkDto =
    PublicVideoPlaylistLinkDto(
        label = label,
        href = href,
    )

fun PublicShortformPlaylistWindow.toDto(): PublicShortformPlaylistWindowDto =
    PublicShortformPlaylistWindowDto(
        items = items.map { it.toDto() },
        currentIndexInWindow = currentIndexInWindow,
        currentPage = currentPage,
        pageSize = pageSize,
        totalItems = totalItems,
        totalPages = totalPages,
    )

fun PublicVideoDetail.toDto(): PublicVideoDetailResponse =
    PublicVideoDetailResponse(
        videoId = videoId,
        title = title,
        sourceTitle = sourceTitle,
        preacherName = preacherName,
        publishedAt = publishedAt,
        thumbnailUrl = thumbnailUrl,
        scriptureReference = scriptureReference,
        scriptureBody = scriptureBody,
        messageBody = messageBody,
        summary = summary,
        description = description,
        contentForm = contentForm,
        playlists = playlists.map { it.toDto() },
        related = related.map { it.toDto() },
        shortformPlaylist = shortformPlaylist?.toDto(),
    )

fun AdminVideoSummary.toDto(): AdminVideoSummaryDto =
    AdminVideoSummaryDto(
        videoId = videoId,
        title = title,
        sourceTitle = sourceTitle,
        preacherName = preacherName,
        publishedAt = publishedAt,
        hidden = hidden,
        contentForm = contentForm,
        thumbnailUrl = thumbnailUrl,
        scriptureReference = scriptureReference,
    )

fun AdminVideoDetail.toDto(): AdminVideoDetailResponse =
    AdminVideoDetailResponse(
        videoId = videoId,
        sourceTitle = sourceTitle,
        sourceDescription = sourceDescription,
        sourcePublishedAt = sourcePublishedAt,
        sourceThumbnailUrl = sourceThumbnailUrl,
        title = title,
        preacherName = preacherName,
        publishedAt = publishedAt,
        hidden = hidden,
        scriptureReference = scriptureReference,
        scriptureBody = scriptureBody,
        messageBody = messageBody,
        summary = summary,
        thumbnailOverrideUrl = thumbnailOverrideUrl,
        contentForm = contentForm,
        publicHref = publicHref,
    )

fun UpdateVideoMetaRequest.toCommand(): UpdateVideoMetaCommand =
    UpdateVideoMetaCommand(
        displayTitle = displayTitle,
        preacherName = preacherName,
        displayPublishedAt = displayPublishedAt,
        hidden = hidden,
        scriptureReference = scriptureReference,
        scriptureBody = scriptureBody,
        messageBody = messageBody,
        summary = summary,
        thumbnailOverrideUrl = thumbnailOverrideUrl,
    )
