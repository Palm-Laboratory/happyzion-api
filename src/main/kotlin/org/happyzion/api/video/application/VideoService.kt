package org.happyzion.api.video.application

import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.menu.application.PublicVideoMenuPathSupport
import org.happyzion.api.menu.domain.MenuItem
import org.happyzion.api.menu.domain.MenuStatus
import org.happyzion.api.menu.domain.MenuType
import org.happyzion.api.menu.infrastructure.persistence.MenuItemRepository
import org.happyzion.api.video.domain.VideoMeta
import org.happyzion.api.video.infrastructure.persistence.VideoMetaRepository
import org.happyzion.api.youtube.domain.YouTubeContentForm
import org.happyzion.api.youtube.domain.YouTubePrivacyStatus
import org.happyzion.api.youtube.domain.YouTubeSyncStatus
import org.happyzion.api.youtube.domain.YouTubeVideo
import org.happyzion.api.youtube.infrastructure.persistence.YouTubePlaylistItemRepository
import org.happyzion.api.youtube.infrastructure.persistence.YouTubePlaylistRepository
import org.happyzion.api.youtube.infrastructure.persistence.YouTubeVideoRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class VideoService(
    private val youTubeVideoRepository: YouTubeVideoRepository,
    private val videoMetaRepository: VideoMetaRepository,
    private val youTubePlaylistItemRepository: YouTubePlaylistItemRepository,
    private val youTubePlaylistRepository: YouTubePlaylistRepository,
    private val menuItemRepository: MenuItemRepository,
) {
    companion object {
        private const val MAX_PAGE_SIZE = 24
        private const val SHORTFORM_DETAIL_PAGE_SIZE = 8
    }

    @Transactional(readOnly = true)
    fun getPublicPlaylistVideosByPath(path: String, page: Int, size: Int): PublicVideoList {
        val menu = resolvePublishedPlaylistMenuByPath(path)
        return toPublicVideoList(menu, loadPlaylistSummaries(menu), page, size)
    }

    @Transactional(readOnly = true)
    fun getPublicPlaylistVideoDetailByPath(path: String, videoId: String): PublicVideoDetail {
        val menu = resolvePublishedPlaylistMenuByPath(path)
        return buildPlaylistVideoDetail(menu, videoId)
    }

    private fun buildPlaylistVideoDetail(menu: MenuItem, videoId: String): PublicVideoDetail {
        val orderedVideos = loadOrderedPlaylistVideos(menu)
        val orderedSummaries = orderedVideos
            .map { toPublicSummary(it.video, it.meta, buildPlaylistVideoHref(menu, it.video.videoId)) }
        val targetIndex = orderedVideos.indexOfFirst { it.video.videoId == videoId }
        val target = orderedVideos.getOrNull(targetIndex)
            ?: throw NotFoundException("재생목록에서 영상을 찾을 수 없습니다. menuId=${menu.id}, videoId=$videoId")

        val related = orderedSummaries
            .filter { it.videoId != videoId }
            .take(6)
        val previousVideo = if (targetIndex > 0) orderedSummaries[targetIndex - 1] else null
        val nextVideo = if (targetIndex < orderedSummaries.lastIndex) orderedSummaries[targetIndex + 1] else null
        val detailContentForm =
            if (menu.playlistContentForm == YouTubeContentForm.SHORTFORM ||
                target.video.contentForm == YouTubeContentForm.SHORTFORM
            ) {
                YouTubeContentForm.SHORTFORM
            } else {
                YouTubeContentForm.LONGFORM
            }

        return PublicVideoDetail(
            videoId = target.video.videoId,
            title = effectiveDisplayTitle(target.video, target.meta),
            sourceTitle = menu.label,
            preacherName = target.meta?.preacherName,
            publishedAt = effectivePublishedAt(target.video, target.meta),
            thumbnailUrl = effectiveThumbnailUrl(target.video, target.meta),
            scriptureReference = target.meta?.scriptureReference,
            scriptureBody = target.meta?.scriptureBody,
            summary = target.meta?.summary,
            description = target.video.description,
            contentForm = detailContentForm,
            playlists = buildPlaylistLinks(target.video.id!!, menu.id!!),
            related = related,
            previousVideo = previousVideo,
            nextVideo = nextVideo,
            shortformPlaylist =
                if (detailContentForm == YouTubeContentForm.SHORTFORM) {
                    buildShortformPlaylistWindow(orderedSummaries, targetIndex)
                } else {
                    null
                },
        )
    }

    @Transactional(readOnly = true)
    fun getAdminVideos(form: YouTubeContentForm?): List<AdminVideoSummary> =
        loadAllVideos()
            .filter { form == null || it.video.contentForm == form }
            .sortedByDescending { effectivePublishedAt(it.video, it.meta) ?: OffsetDateTime.MIN }
            .map { (video, meta) ->
                AdminVideoSummary(
                    videoId = video.videoId,
                    title = effectiveDisplayTitle(video, meta),
                    sourceTitle = video.title,
                    preacherName = meta?.preacherName,
                    publishedAt = effectivePublishedAt(video, meta),
                    hidden = meta?.hidden ?: false,
                    contentForm = video.contentForm,
                    thumbnailUrl = effectiveThumbnailUrl(video, meta),
                    scriptureReference = meta?.scriptureReference,
                )
            }

    @Transactional(readOnly = true)
    fun getAdminVideosByMenu(menuId: Long): List<AdminVideoSummary> {
        val menu = menuItemRepository.findByIdOrNull(menuId)
            ?: throw NotFoundException("메뉴를 찾을 수 없습니다. menuId=$menuId")

        if (menu.type != MenuType.YOUTUBE_PLAYLIST) {
            throw NotFoundException("영상 재생목록 메뉴가 아닙니다. menuId=$menuId")
        }

        val playlistId = menu.playlistId
            ?: throw NotFoundException("재생목록 정보가 연결되지 않았습니다. menuId=$menuId")

        val playlistItems = youTubePlaylistItemRepository.findAllByPlaylistIdOrderByPositionAsc(playlistId)
        if (playlistItems.isEmpty()) return emptyList()

        val videoIds = playlistItems.map { it.videoId }.distinct()
        val videosById = youTubeVideoRepository.findAllById(videoIds).associateBy { it.id!! }
        val metasByVideoId = videoMetaRepository.findAllByVideoIdIn(videoIds).associateBy { it.videoId }

        return playlistItems.mapNotNull { playlistItem ->
            val video = videosById[playlistItem.videoId] ?: return@mapNotNull null
            val meta = metasByVideoId[playlistItem.videoId]
            AdminVideoSummary(
                videoId = video.videoId,
                title = effectiveDisplayTitle(video, meta),
                sourceTitle = video.title,
                preacherName = meta?.preacherName,
                publishedAt = effectivePublishedAt(video, meta),
                hidden = meta?.hidden ?: false,
                contentForm = video.contentForm,
                thumbnailUrl = effectiveThumbnailUrl(video, meta),
                scriptureReference = meta?.scriptureReference,
            )
        }
    }

    @Transactional(readOnly = true)
    fun getAdminVideoDetail(videoId: String): AdminVideoDetail {
        val video = youTubeVideoRepository.findByVideoId(videoId)
            ?: throw NotFoundException("영상을 찾을 수 없습니다. videoId=$videoId")
        val meta = videoMetaRepository.findByVideoId(video.id!!)

        return AdminVideoDetail(
            videoId = video.videoId,
            sourceTitle = video.title,
            sourceDescription = video.description,
            sourcePublishedAt = video.publishedAt,
            sourceThumbnailUrl = video.thumbnailUrl,
            title = effectiveDisplayTitle(video, meta),
            preacherName = meta?.preacherName,
            publishedAt = effectivePublishedAt(video, meta),
            hidden = meta?.hidden ?: false,
            scriptureReference = meta?.scriptureReference,
            scriptureBody = meta?.scriptureBody,
            summary = meta?.summary,
            contentForm = video.contentForm,
            publicHref = buildAdminPublicHref(video.id, video.videoId),
        )
    }

    @Transactional
    fun updateAdminVideoMeta(videoId: String, command: UpdateVideoMetaCommand): AdminVideoDetail {
        val video = youTubeVideoRepository.findByVideoId(videoId)
            ?: throw NotFoundException("영상을 찾을 수 없습니다. videoId=$videoId")
        val existing = videoMetaRepository.findByVideoId(video.id!!)
        val meta = existing ?: VideoMeta(videoId = video.id)

        meta.displayTitle = command.displayTitle?.trim()?.ifBlank { null }
        meta.preacherName = command.preacherName?.trim()?.ifBlank { null }
        meta.displayPublishedAt = command.displayPublishedAt
        meta.hidden = command.hidden
        meta.scriptureReference = command.scriptureReference?.trim()?.ifBlank { null }
        meta.scriptureBody = command.scriptureBody?.trim()?.ifBlank { null }
        meta.summary = command.summary?.trim()?.ifBlank { null }

        videoMetaRepository.save(meta)
        return getAdminVideoDetail(videoId)
    }

    private fun loadAllVideos(): List<VideoWithMeta> {
        val metasByVideoId = videoMetaRepository.findAll().associateBy { it.videoId }
        return youTubeVideoRepository.findAll()
            .map { video -> VideoWithMeta(video = video, meta = metasByVideoId[video.id]) }
    }

    private fun loadPlaylistSummaries(menu: MenuItem): List<PublicVideoSummary> =
        loadOrderedPlaylistVideos(menu)
            .map { toPublicSummary(it.video, it.meta, buildPlaylistVideoHref(menu, it.video.videoId)) }

    private fun loadOrderedPlaylistVideos(menu: MenuItem): List<VideoWithMeta> {
        val playlistId = menu.playlistId
            ?: throw NotFoundException("재생목록 정보가 연결되지 않았습니다. menuId=${menu.id}")
        val playlistItems = youTubePlaylistItemRepository.findAllByPlaylistIdOrderByPositionAsc(playlistId)
        if (playlistItems.isEmpty()) {
            return emptyList()
        }

        val videoIds = playlistItems.map { it.videoId }.distinct()
        val videosById = youTubeVideoRepository.findAllById(videoIds).associateBy { it.id!! }
        val metasByVideoId = videoMetaRepository.findAllByVideoIdIn(videoIds).associateBy { it.videoId }

        return playlistItems.mapNotNull { playlistItem ->
            val video = videosById[playlistItem.videoId] ?: return@mapNotNull null
            val meta = metasByVideoId[playlistItem.videoId]
            val candidate = VideoWithMeta(video = video, meta = meta)

            if (isDisplayable(candidate.video, candidate.meta)) {
                candidate
            } else {
                null
            }
        }
    }

    private fun buildShortformPlaylistWindow(
        summaries: List<PublicVideoSummary>,
        currentIndex: Int,
    ): PublicShortformPlaylistWindow {
        val pageSize = SHORTFORM_DETAIL_PAGE_SIZE.coerceIn(1, MAX_PAGE_SIZE)
        val totalItems = summaries.size
        val totalPages = if (totalItems == 0) 1 else ((totalItems - 1) / pageSize) + 1
        val currentPage = (currentIndex / pageSize) + 1
        val fromIndex = ((currentPage - 1) * pageSize).coerceAtMost(totalItems)
        val toIndex = (fromIndex + pageSize).coerceAtMost(totalItems)

        return PublicShortformPlaylistWindow(
            items = summaries.subList(fromIndex, toIndex),
            currentIndexInWindow = currentIndex - fromIndex,
            currentPage = currentPage,
            pageSize = pageSize,
            totalItems = totalItems,
            totalPages = totalPages,
        )
    }

    private fun toPublicVideoList(
        menu: MenuItem,
        summaries: List<PublicVideoSummary>,
        page: Int,
        size: Int,
    ): PublicVideoList {
        val form = menu.playlistContentForm ?: YouTubeContentForm.LONGFORM
        val resolvedPageSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val resolvedPage = page.coerceAtLeast(1)
        val featured = if (form == YouTubeContentForm.LONGFORM) summaries.firstOrNull() else null
        val pagedSource = summaries
        val totalItems = pagedSource.size
        val totalPages = if (totalItems == 0) 1 else ((totalItems - 1) / resolvedPageSize) + 1
        val safePage = resolvedPage.coerceAtMost(totalPages)
        val fromIndex = ((safePage - 1) * resolvedPageSize).coerceAtMost(totalItems)
        val toIndex = (fromIndex + resolvedPageSize).coerceAtMost(totalItems)

        return PublicVideoList(
            form = form,
            featured = featured,
            items = pagedSource.subList(fromIndex, toIndex),
            currentPage = safePage,
            pageSize = resolvedPageSize,
            totalItems = totalItems,
            totalPages = totalPages,
        )
    }

    private fun isDisplayable(video: YouTubeVideo, meta: VideoMeta?): Boolean =
        video.syncStatus == YouTubeSyncStatus.ACTIVE &&
            video.privacyStatus != YouTubePrivacyStatus.PRIVATE &&
            !(meta?.hidden ?: false)

    private fun effectiveDisplayTitle(video: YouTubeVideo, meta: VideoMeta?): String =
        meta?.displayTitle?.takeIf { it.isNotBlank() } ?: video.title

    private fun effectivePublishedAt(video: YouTubeVideo, meta: VideoMeta?): OffsetDateTime? =
        meta?.displayPublishedAt ?: video.publishedAt

    private fun effectiveThumbnailUrl(video: YouTubeVideo, meta: VideoMeta?): String? = video.thumbnailUrl

    private fun buildPlaylistLinks(videoEntityId: Long, currentMenuId: Long): List<PublicVideoPlaylistLink> =
        run {
            val publishedItems = menuItemRepository.findAllByStatusOrderBySortOrderAscIdAsc(MenuStatus.PUBLISHED)
            val itemsById = publishedItems.associateBy { it.id!! }

            youTubePlaylistItemRepository.findAllByVideoId(videoEntityId)
            .mapNotNull { playlistItem ->
                val playlist = youTubePlaylistRepository.findById(playlistItem.playlistId).orElse(null) ?: return@mapNotNull null
                val menu = publishedItems
                    .firstOrNull { it.playlistId == playlist.id }
                    ?: return@mapNotNull null
                PublicVideoPlaylistLink(
                    label = menu.label,
                    href = buildPlaylistPath(menu, itemsById),
                )
            }
            .distinctBy { it.href }
        }

    private fun buildAdminPublicHref(videoEntityId: Long, videoId: String): String? =
        findPublicMenuHrefForVideoEntity(videoEntityId, videoId)

    private fun findPublicMenuHrefForVideoEntity(videoEntityId: Long, videoId: String): String? =
        run {
            val publishedItems = menuItemRepository.findAllByStatusOrderBySortOrderAscIdAsc(MenuStatus.PUBLISHED)
            val itemsById = publishedItems.associateBy { it.id!! }

            youTubePlaylistItemRepository.findAllByVideoId(videoEntityId)
            .mapNotNull { playlistItem ->
                val playlist = youTubePlaylistRepository.findByIdOrNull(playlistItem.playlistId) ?: return@mapNotNull null
                val menu = publishedItems
                    .firstOrNull {
                        it.playlistId == playlist.id &&
                            it.type == MenuType.YOUTUBE_PLAYLIST &&
                            it.status == MenuStatus.PUBLISHED
                    }
                    ?: return@mapNotNull null
                "${buildPlaylistPath(menu, itemsById)}/$videoId"
            }
            .firstOrNull()
        }

    private fun toPublicSummary(
        video: YouTubeVideo,
        meta: VideoMeta?,
        href: String,
    ): PublicVideoSummary =
        PublicVideoSummary(
            videoId = video.videoId,
            title = effectiveDisplayTitle(video, meta),
            preacherName = meta?.preacherName,
            publishedAt = effectivePublishedAt(video, meta),
            thumbnailUrl = effectiveThumbnailUrl(video, meta),
            scriptureReference = meta?.scriptureReference,
            summary = meta?.summary ?: video.description,
            contentForm = video.contentForm,
            href = href,
        )

    private fun buildPlaylistVideoHref(menu: MenuItem, videoId: String): String {
        val publishedItems = menuItemRepository.findAllByStatusOrderBySortOrderAscIdAsc(MenuStatus.PUBLISHED)
        val itemsById = publishedItems.associateBy { it.id!! }
        return "${buildPlaylistPath(menu, itemsById)}/$videoId"
    }

    private fun buildPlaylistPath(menu: MenuItem, itemsById: Map<Long, MenuItem>): String =
        PublicVideoMenuPathSupport.buildPlaylistPath(menu, itemsById)

    private fun resolvePublishedPlaylistMenuByPath(path: String): MenuItem {
        val publishedItems = menuItemRepository.findAllByStatusOrderBySortOrderAscIdAsc(MenuStatus.PUBLISHED)
        val itemsById = publishedItems.associateBy { it.id!! }
        val normalizedPath = normalizeLookupPath(path)

        return publishedItems.firstOrNull { item ->
            item.type == MenuType.YOUTUBE_PLAYLIST &&
                PublicVideoMenuPathSupport.matchesPlaylistPath(item, itemsById, normalizedPath)
        } ?: throw NotFoundException("재생목록을 찾을 수 없습니다. path=$path")
    }

    private fun normalizeLookupPath(path: String): String {
        val trimmed = path.substringBefore('?').substringBefore('#').trim()
        if (trimmed.isBlank()) {
            return "/"
        }
        return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }

    private data class VideoWithMeta(
        val video: YouTubeVideo,
        val meta: VideoMeta?,
    )

}
