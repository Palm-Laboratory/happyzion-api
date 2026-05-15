package org.happyzion.api.youtube.application

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.happyzion.api.common.config.EvictPublicMenuCache
import org.happyzion.api.common.config.YouTubeProperties
import org.happyzion.api.menu.application.AdminYouTubePlaylistSummary
import org.happyzion.api.menu.application.MenuSlugSupport
import org.happyzion.api.menu.application.YouTubeSyncSummary
import org.happyzion.api.menu.domain.MenuItem
import org.happyzion.api.menu.domain.MenuStatus
import org.happyzion.api.menu.domain.MenuType
import org.happyzion.api.menu.infrastructure.persistence.MenuItemRepository
import org.happyzion.api.youtube.domain.YouTubeChannel
import org.happyzion.api.youtube.domain.YouTubeContentForm
import org.happyzion.api.youtube.domain.YouTubePlaylist
import org.happyzion.api.youtube.domain.YouTubePlaylistItem
import org.happyzion.api.youtube.domain.YouTubePrivacyStatus
import org.happyzion.api.youtube.domain.YouTubeSyncStatus
import org.happyzion.api.youtube.domain.YouTubeVideo
import org.happyzion.api.youtube.infrastructure.persistence.YouTubeChannelRepository
import org.happyzion.api.youtube.infrastructure.persistence.YouTubePlaylistItemRepository
import org.happyzion.api.youtube.infrastructure.persistence.YouTubePlaylistRepository
import org.happyzion.api.youtube.infrastructure.persistence.YouTubeVideoRepository
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class YouTubeSyncService(
    private val youTubeProperties: YouTubeProperties,
    private val youTubeChannelRepository: YouTubeChannelRepository,
    private val youTubePlaylistRepository: YouTubePlaylistRepository,
    private val youTubeVideoRepository: YouTubeVideoRepository,
    private val youTubePlaylistItemRepository: YouTubePlaylistItemRepository,
    private val playlistDisplayableVideoCountResolver: PlaylistDisplayableVideoCountResolver,
    private val menuItemRepository: MenuItemRepository,
    private val objectMapper: ObjectMapper,
) {
    private val restClient = RestClient.builder()
        .baseUrl("https://www.googleapis.com/youtube/v3")
        .build()

    @Transactional
    @EvictPublicMenuCache
    fun sync(): YouTubeSyncSummary {
        require(isConfigured()) { "YOUTUBE_API_KEY와 YOUTUBE_CHANNEL_ID를 먼저 설정해 주세요." }

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val playlistPayloads = fetchAllPlaylists()

        val channelTitle = playlistPayloads.firstOrNull()?.channelTitle ?: "Happy Zion"
        val channel = upsertChannel(channelTitle, now)
        val existingPlaylists = youTubePlaylistRepository.findAllByChannelId(channel.id!!)
        val existingByPlaylistId = existingPlaylists.associateBy { it.playlistId }
        val existingVideosByVideoId = youTubeVideoRepository.findAllByChannelId(channel.id).associateBy { it.videoId }
        val seenPlaylistIds = linkedSetOf<String>()
        val playlistItemsByPlaylistId = linkedMapOf<String, List<PlaylistItemPayload>>()

        var createdMenus = 0
        var updatedMenus = 0
        var archivedMenus = 0
        var restoredMenus = 0

        playlistPayloads.forEach { payload ->
            seenPlaylistIds += payload.playlistId
            val playlist = existingByPlaylistId[payload.playlistId]
            val persistedPlaylist = if (playlist == null) {
                youTubePlaylistRepository.save(
                    YouTubePlaylist(
                        channelId = channel.id,
                        playlistId = payload.playlistId,
                        title = payload.title,
                        description = payload.description,
                        thumbnailUrl = payload.thumbnailUrl,
                        itemCount = payload.itemCount,
                        publishedAt = payload.publishedAt,
                        syncStatus = YouTubeSyncStatus.ACTIVE,
                        lastSyncedAt = now,
                    )
                )
            } else {
                playlist.title = payload.title
                playlist.description = payload.description
                playlist.thumbnailUrl = payload.thumbnailUrl
                playlist.itemCount = payload.itemCount
                playlist.publishedAt = payload.publishedAt
                playlist.syncStatus = YouTubeSyncStatus.ACTIVE
                playlist.lastSyncedAt = now
                youTubePlaylistRepository.save(playlist)
            }

            playlistItemsByPlaylistId[persistedPlaylist.playlistId] = fetchPlaylistItems(persistedPlaylist.playlistId)

            val menu = menuItemRepository.findAllByOrderBySortOrderAscIdAsc()
                .firstOrNull { it.playlistId == persistedPlaylist.id }

            if (menu == null) {
                menuItemRepository.save(
                    MenuItem(
                        parentId = null,
                        type = MenuType.YOUTUBE_PLAYLIST,
                        status = MenuStatus.DRAFT,
                        label = persistedPlaylist.title,
                        slug = generateUniqueSlug(persistedPlaylist.title),
                        playlistId = persistedPlaylist.id,
                        isAuto = true,
                        playlistContentForm = YouTubeContentForm.LONGFORM,
                    )
                )
                createdMenus += 1
            } else {
                if (!menu.labelCustomized && menu.label != persistedPlaylist.title) {
                    menu.label = persistedPlaylist.title
                    updatedMenus += 1
                }
                val normalizedSlug = generateUniqueSlug(persistedPlaylist.title, menu.id)
                if (!menu.slugCustomized) {
                    if (menu.slug != normalizedSlug) {
                        menu.slug = normalizedSlug
                        updatedMenus += 1
                    }
                }
                if (menu.status == MenuStatus.ARCHIVED && persistedPlaylist.syncStatus == YouTubeSyncStatus.ACTIVE) {
                    menu.status = MenuStatus.DRAFT
                    restoredMenus += 1
                }
                menuItemRepository.save(menu)
            }
        }

        val allVideoIds = playlistItemsByPlaylistId.values
            .flatten()
            .map { it.videoId }
            .distinct()
        val videoPayloadsByVideoId = fetchVideosByIds(allVideoIds).associateBy { it.videoId }
        val persistedVideosByVideoId = linkedMapOf<String, YouTubeVideo>()

        allVideoIds.forEach { videoId ->
            val payload = videoPayloadsByVideoId[videoId] ?: return@forEach
            val existingVideo = existingVideosByVideoId[videoId]
            val persistedVideo = if (existingVideo == null) {
                youTubeVideoRepository.save(
                    YouTubeVideo(
                        channelId = channel.id,
                        videoId = payload.videoId,
                        title = payload.title,
                        description = payload.description,
                        thumbnailUrl = payload.thumbnailUrl,
                        publishedAt = payload.publishedAt,
                        durationSeconds = payload.durationSeconds,
                        contentForm = payload.contentForm,
                        privacyStatus = payload.privacyStatus,
                        syncStatus = YouTubeSyncStatus.ACTIVE,
                        lastSyncedAt = now,
                    )
                )
            } else {
                existingVideo.title = payload.title
                existingVideo.description = payload.description
                existingVideo.thumbnailUrl = payload.thumbnailUrl
                existingVideo.publishedAt = payload.publishedAt
                existingVideo.durationSeconds = payload.durationSeconds
                existingVideo.contentForm = payload.contentForm
                existingVideo.privacyStatus = payload.privacyStatus
                existingVideo.syncStatus = YouTubeSyncStatus.ACTIVE
                existingVideo.lastSyncedAt = now
                youTubeVideoRepository.save(existingVideo)
            }
            persistedVideosByVideoId[videoId] = persistedVideo
        }

        val persistedPlaylistsByPlaylistId = youTubePlaylistRepository.findAllByChannelId(channel.id)
            .associateBy { it.playlistId }
        playlistItemsByPlaylistId.forEach { (playlistId, itemPayloads) ->
            val persistedPlaylist = persistedPlaylistsByPlaylistId[playlistId] ?: return@forEach
            youTubePlaylistItemRepository.deleteAllByPlaylistId(persistedPlaylist.id!!)
            youTubePlaylistItemRepository.flush()

            deduplicatePlaylistItems(itemPayloads).forEach { itemPayload ->
                val persistedVideo = persistedVideosByVideoId[itemPayload.videoId] ?: return@forEach
                youTubePlaylistItemRepository.save(
                    YouTubePlaylistItem(
                        playlistId = persistedPlaylist.id,
                        videoId = persistedVideo.id!!,
                        position = itemPayload.position,
                        addedAt = itemPayload.addedAt,
                    )
                )
            }
        }

        existingPlaylists
            .filter { it.playlistId !in seenPlaylistIds }
            .forEach { playlist ->
                if (playlist.syncStatus != YouTubeSyncStatus.REMOVED) {
                    playlist.syncStatus = YouTubeSyncStatus.REMOVED
                    playlist.lastSyncedAt = now
                    youTubePlaylistRepository.save(playlist)
                }

                playlist.id?.let(youTubePlaylistItemRepository::deleteAllByPlaylistId)

                menuItemRepository.findAllByOrderBySortOrderAscIdAsc()
                    .firstOrNull { it.playlistId == playlist.id }
                    ?.let { menu ->
                        if (menu.status != MenuStatus.ARCHIVED) {
                            menu.status = MenuStatus.ARCHIVED
                            menuItemRepository.save(menu)
                            archivedMenus += 1
                        }
                    }
            }

        existingVideosByVideoId.values
            .filter { it.videoId !in allVideoIds }
            .forEach { video ->
                if (video.syncStatus != YouTubeSyncStatus.REMOVED) {
                    video.syncStatus = YouTubeSyncStatus.REMOVED
                    video.lastSyncedAt = now
                    youTubeVideoRepository.save(video)
                }
            }

        channel.lastSyncedAt = now
        youTubeChannelRepository.save(channel)

        recomputeMenuPaths()

        return YouTubeSyncSummary(
            status = "OK",
            totalPlaylists = playlistPayloads.size,
            createdMenus = createdMenus,
            updatedMenus = updatedMenus,
            archivedMenus = archivedMenus,
            restoredMenus = restoredMenus,
            completedAt = now.toString(),
        )
    }

    @Transactional(readOnly = true)
    fun getPlaylistSummaries(): List<AdminYouTubePlaylistSummary> {
        val menus = menuItemRepository.findAllByOrderBySortOrderAscIdAsc()
        val menusById = menus.associateBy { it.id!! }
        val playlistsById = youTubePlaylistRepository.findAll().associateBy { it.id!! }
        val displayableCountsByPlaylistId = playlistDisplayableVideoCountResolver.resolveAll(playlistsById.keys)

        return menus
            .filter { it.type == MenuType.YOUTUBE_PLAYLIST && it.playlistId != null }
            .sortedWith(compareBy<MenuItem> { it.status }.thenBy { it.sortOrder }.thenBy { it.id })
            .mapNotNull { menu ->
                val playlist = playlistsById[menu.playlistId] ?: return@mapNotNull null
                val parent = menu.parentId?.let(menusById::get)
                AdminYouTubePlaylistSummary(
                    menuId = menu.id!!,
                    playlistId = playlist.playlistId,
                    menuLabel = menu.label,
                    sourceTitle = playlist.title,
                    slug = menu.slug,
                    status = menu.status,
                    syncStatus = playlist.syncStatus,
                    parentId = parent?.id,
                    parentLabel = parent?.label,
                    thumbnailUrl = playlist.thumbnailUrl,
                    itemCount = displayableCountsByPlaylistId[playlist.id] ?: 0,
                    playlistContentForm = menu.playlistContentForm ?: YouTubeContentForm.LONGFORM,
                )
            }
    }

    fun isConfigured(): Boolean =
        youTubeProperties.apiKey.isNotBlank() && youTubeProperties.channelId.isNotBlank()

    private fun upsertChannel(channelTitle: String, now: OffsetDateTime): YouTubeChannel {
        val existing = youTubeChannelRepository.findByChannelId(youTubeProperties.channelId)
        return if (existing == null) {
            youTubeChannelRepository.save(
                YouTubeChannel(
                    channelId = youTubeProperties.channelId,
                    title = channelTitle,
                    lastSyncedAt = now,
                )
            )
        } else {
            existing.title = channelTitle
            existing.lastSyncedAt = now
            youTubeChannelRepository.save(existing)
        }
    }

    private fun fetchAllPlaylists(): List<PlaylistPayload> {
        val playlists = mutableListOf<PlaylistPayload>()
        var pageToken: String? = null

        do {
            val responseBody = restClient.get()
                .uri { uriBuilder ->
                    uriBuilder.path("/playlists")
                        .queryParam("part", "snippet,contentDetails")
                        .queryParam("maxResults", "50")
                        .queryParam("channelId", youTubeProperties.channelId)
                        .queryParam("key", youTubeProperties.apiKey)
                        .apply {
                            if (!pageToken.isNullOrBlank()) {
                                queryParam("pageToken", pageToken)
                            }
                        }
                        .build()
                }
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String::class.java)
                ?: throw IllegalStateException("유튜브 API 응답이 비어 있습니다.")

            val json = objectMapper.readTree(responseBody)
            val items = json["items"] ?: objectMapper.createArrayNode()
            items.forEach { item ->
                playlists += item.toPlaylistPayload()
            }
            pageToken = json["nextPageToken"]?.asText()
        } while (!pageToken.isNullOrBlank())

        return playlists
    }

    private fun fetchPlaylistItems(playlistId: String): List<PlaylistItemPayload> {
        val items = mutableListOf<PlaylistItemPayload>()
        var pageToken: String? = null

        do {
            val responseBody = restClient.get()
                .uri { uriBuilder ->
                    uriBuilder.path("/playlistItems")
                        .queryParam("part", "snippet,contentDetails")
                        .queryParam("maxResults", "50")
                        .queryParam("playlistId", playlistId)
                        .queryParam("key", youTubeProperties.apiKey)
                        .apply {
                            if (!pageToken.isNullOrBlank()) {
                                queryParam("pageToken", pageToken)
                            }
                        }
                        .build()
                }
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String::class.java)
                ?: throw IllegalStateException("유튜브 재생목록 아이템 응답이 비어 있습니다.")

            val json = objectMapper.readTree(responseBody)
            val payloadItems = json["items"] ?: objectMapper.createArrayNode()
            payloadItems.forEach { item ->
                item.toPlaylistItemPayload()?.let(items::add)
            }
            pageToken = json["nextPageToken"]?.asText()
        } while (!pageToken.isNullOrBlank())

        return items.sortedBy { it.position }
    }

    private fun deduplicatePlaylistItems(items: List<PlaylistItemPayload>): List<PlaylistItemPayload> {
        val deduplicated = linkedMapOf<String, PlaylistItemPayload>()

        items.sortedBy { it.position }.forEach { item ->
            deduplicated.putIfAbsent(item.videoId, item)
        }

        return deduplicated.values.mapIndexed { index, item ->
            item.copy(position = index)
        }
    }

    private fun fetchVideosByIds(videoIds: List<String>): List<VideoPayload> {
        if (videoIds.isEmpty()) {
            return emptyList()
        }

        return videoIds.chunked(50).flatMap { chunk ->
            val responseBody = restClient.get()
                .uri { uriBuilder ->
                    uriBuilder.path("/videos")
                        .queryParam("part", "snippet,contentDetails,status")
                        .queryParam("id", chunk.joinToString(","))
                        .queryParam("key", youTubeProperties.apiKey)
                        .build()
                }
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String::class.java)
                ?: throw IllegalStateException("유튜브 영상 응답이 비어 있습니다.")

            val json = objectMapper.readTree(responseBody)
            val items = json["items"] ?: objectMapper.createArrayNode()
            items.map { it.toVideoPayload() }
        }
    }

    private fun JsonNode.toPlaylistPayload(): PlaylistPayload {
        val snippet = this["snippet"]
        val contentDetails = this["contentDetails"]
        val thumbnails = snippet["thumbnails"]
        val thumbnailUrl =
            thumbnails["maxres"]?.get("url")?.asText()
                ?: thumbnails["high"]?.get("url")?.asText()
                ?: thumbnails["medium"]?.get("url")?.asText()
                ?: thumbnails["default"]?.get("url")?.asText()

        return PlaylistPayload(
            playlistId = this["id"].asText(),
            title = snippet["title"].asText(),
            description = snippet["description"]?.asText(),
            thumbnailUrl = thumbnailUrl,
            itemCount = contentDetails["itemCount"]?.asInt() ?: 0,
            publishedAt = snippet["publishedAt"]?.asText()?.let(OffsetDateTime::parse),
            channelTitle = snippet["channelTitle"]?.asText() ?: "Happy Zion",
        )
    }

    private fun JsonNode.toPlaylistItemPayload(): PlaylistItemPayload? {
        val snippet = this["snippet"]
        val contentDetails = this["contentDetails"]
        val videoId = contentDetails["videoId"]?.asText()?.takeIf { it.isNotBlank() } ?: return null

        return PlaylistItemPayload(
            videoId = videoId,
            position = snippet["position"]?.asInt() ?: 0,
            addedAt = snippet["publishedAt"]?.asText()?.let(OffsetDateTime::parse),
        )
    }

    private fun JsonNode.toVideoPayload(): VideoPayload {
        val snippet = this["snippet"]
        val contentDetails = this["contentDetails"]
        val status = this["status"]
        val thumbnails = snippet["thumbnails"]
        val thumbnailUrl =
            thumbnails["maxres"]?.get("url")?.asText()
                ?: thumbnails["high"]?.get("url")?.asText()
                ?: thumbnails["medium"]?.get("url")?.asText()
                ?: thumbnails["default"]?.get("url")?.asText()
        val durationSeconds = parseDurationSeconds(contentDetails["duration"]?.asText())
        val privacyStatus = when (status["privacyStatus"]?.asText()?.uppercase()) {
            "PRIVATE" -> YouTubePrivacyStatus.PRIVATE
            "UNLISTED" -> YouTubePrivacyStatus.UNLISTED
            else -> YouTubePrivacyStatus.PUBLIC
        }

        return VideoPayload(
            videoId = this["id"].asText(),
            title = snippet["title"].asText(),
            description = snippet["description"]?.asText(),
            thumbnailUrl = thumbnailUrl,
            publishedAt = snippet["publishedAt"]?.asText()?.let(OffsetDateTime::parse),
            durationSeconds = durationSeconds,
            contentForm = if ((durationSeconds ?: Int.MAX_VALUE) <= 180) {
                YouTubeContentForm.SHORTFORM
            } else {
                YouTubeContentForm.LONGFORM
            },
            privacyStatus = privacyStatus,
        )
    }

    private fun parseDurationSeconds(rawDuration: String?): Int? =
        rawDuration
            ?.takeIf { it.isNotBlank() }
            ?.let { Duration.parse(it).seconds.toInt() }

    private fun generateUniqueSlug(label: String, currentMenuId: Long? = null): String {
        val baseSlug = MenuSlugSupport.slugifyToAscii(label).ifBlank { "playlist" }
        var candidate = baseSlug
        var suffix = 1
        while (menuItemRepository.findBySlug(candidate)?.id?.let { it != currentMenuId } == true) {
            suffix += 1
            candidate = "$baseSlug-$suffix"
        }
        return candidate
    }

    private fun requiresAutoSlugMigration(slug: String): Boolean =
        slug != MenuSlugSupport.slugifyToAscii(slug)

    private fun recomputeMenuPaths() {
        val items = menuItemRepository.findAllByOrderBySortOrderAscIdAsc()
        val itemsByParent = items.groupBy { it.parentId }

        fun visit(parentId: Long?, parentPath: String, depth: Int) {
            itemsByParent[parentId]
                .orEmpty()
                .sortedWith(compareBy<MenuItem> { it.sortOrder }.thenBy { it.id })
                .forEach { item ->
                    val itemId = item.id ?: return@forEach
                    item.depth = depth
                    item.path = "$parentPath$itemId/"
                    menuItemRepository.save(item)
                    visit(itemId, item.path, depth + 1)
                }
        }

        visit(parentId = null, parentPath = "/", depth = 0)
    }
}

private data class PlaylistPayload(
    val playlistId: String,
    val title: String,
    val description: String?,
    val thumbnailUrl: String?,
    val itemCount: Int,
    val publishedAt: OffsetDateTime?,
    val channelTitle: String,
)

private data class PlaylistItemPayload(
    val videoId: String,
    val position: Int,
    val addedAt: OffsetDateTime?,
)

private data class VideoPayload(
    val videoId: String,
    val title: String,
    val description: String?,
    val thumbnailUrl: String?,
    val publishedAt: OffsetDateTime?,
    val durationSeconds: Int?,
    val contentForm: YouTubeContentForm,
    val privacyStatus: YouTubePrivacyStatus,
)
