package org.happyzion.api.menu.interfaces.dto

import org.happyzion.api.board.domain.BoardType
import org.happyzion.api.menu.application.AdminMenuSnapshot
import org.happyzion.api.menu.application.AdminYouTubePlaylistSummary
import org.happyzion.api.menu.application.MenuTreeNode
import org.happyzion.api.menu.application.MenuTreeNodeInput
import org.happyzion.api.menu.application.PublicNavigationResponse
import org.happyzion.api.menu.application.PublicResolvedMenuPage
import org.happyzion.api.menu.application.PublicVideoDetail
import org.happyzion.api.menu.application.YouTubeSyncSummary
import org.happyzion.api.menu.domain.MenuStatus
import org.happyzion.api.menu.domain.MenuType
import org.happyzion.api.youtube.domain.YouTubeContentForm
import org.happyzion.api.youtube.domain.YouTubeSyncStatus

data class ReplaceMenuTreeRequest(
    val items: List<MenuTreeNodeRequest>,
)

data class MenuTreeNodeRequest(
    val id: Long? = null,
    val type: MenuType,
    val status: MenuStatus,
    val label: String,
    val slug: String,
    val slugCustomized: Boolean = false,
    val staticPageKey: String? = null,
    val boardKey: String? = null,
    val boardType: BoardType? = null,
    val externalUrl: String? = null,
    val openInNewTab: Boolean = false,
    val isAuto: Boolean = false,
    val playlistContentForm: YouTubeContentForm? = null,
    val children: List<MenuTreeNodeRequest> = emptyList(),
)

data class AdminMenuTreeNodeDto(
    val id: Long,
    val type: MenuType,
    val status: MenuStatus,
    val label: String,
    val slug: String,
    val isAuto: Boolean,
    val labelCustomized: Boolean,
    val slugCustomized: Boolean,
    val staticPageKey: String?,
    val boardKey: String?,
    val boardTypeKey: String?,
    val boardTypeLabel: String?,
    val externalUrl: String?,
    val openInNewTab: Boolean,
    val playlistTitle: String?,
    val playlistSourceTitle: String?,
    val thumbnailUrl: String?,
    val itemCount: Int?,
    val syncStatus: YouTubeSyncStatus?,
    val playlistContentForm: YouTubeContentForm?,
    val parentId: Long?,
    val children: List<AdminMenuTreeNodeDto>,
)

data class AdminMenuTreeResponse(
    val items: List<AdminMenuTreeNodeDto>,
)

data class AdminYouTubePlaylistDto(
    val menuId: Long,
    val playlistId: String,
    val menuLabel: String,
    val sourceTitle: String,
    val slug: String,
    val status: MenuStatus,
    val syncStatus: YouTubeSyncStatus,
    val parentId: Long?,
    val parentLabel: String?,
    val thumbnailUrl: String?,
    val itemCount: Int,
    val playlistContentForm: YouTubeContentForm,
)

data class AdminYouTubePlaylistsResponse(
    val playlists: List<AdminYouTubePlaylistDto>,
)

data class PublicVideoDetailResponse(
    val title: String,
    val sourceTitle: String,
    val playlistId: String,
    val slug: String,
    val fullPath: String,
    val description: String?,
    val thumbnailUrl: String?,
    val itemCount: Int,
    val contentForm: YouTubeContentForm,
    val groupLabel: String?,
    val siblings: List<PublicVideoSiblingDto>,
)

data class PublicVideoSiblingDto(
    val label: String,
    val href: String,
)

data class PublicResolvedMenuPageResponse(
    val menuId: Long,
    val type: MenuType,
    val label: String,
    val slug: String,
    val fullPath: String,
    val parentLabel: String?,
    val staticPageKey: String?,
    val boardKey: String?,
    val redirectTo: String?,
)

data class YouTubeSyncResponse(
    val status: String,
    val totalPlaylists: Int,
    val createdMenus: Int,
    val updatedMenus: Int,
    val archivedMenus: Int,
    val restoredMenus: Int,
    val completedAt: String,
)

fun ReplaceMenuTreeRequest.toCommand(): List<MenuTreeNodeInput> = items.map { it.toCommand() }

private fun MenuTreeNodeRequest.toCommand(): MenuTreeNodeInput =
    MenuTreeNodeInput(
        id = id,
        type = type,
        status = status,
        label = label,
        slug = slug,
        slugCustomized = slugCustomized,
        staticPageKey = staticPageKey,
        boardKey = boardKey,
        boardType = boardType,
        externalUrl = externalUrl,
        openInNewTab = openInNewTab,
        isAuto = isAuto,
        playlistContentForm = playlistContentForm,
        children = children.map { it.toCommand() },
    )

fun AdminMenuSnapshot.toDto(): AdminMenuTreeResponse =
    AdminMenuTreeResponse(items = items.map { it.toDto() })

fun MenuTreeNode.toDto(): AdminMenuTreeNodeDto =
    AdminMenuTreeNodeDto(
        id = id,
        type = type,
        status = status,
        label = label,
        slug = slug,
        isAuto = isAuto,
        labelCustomized = labelCustomized,
        slugCustomized = slugCustomized,
        staticPageKey = staticPageKey,
        boardKey = boardKey,
        boardTypeKey = boardTypeKey,
        boardTypeLabel = boardTypeLabel,
        externalUrl = externalUrl,
        openInNewTab = openInNewTab,
        playlistTitle = playlistTitle,
        playlistSourceTitle = playlistSourceTitle,
        thumbnailUrl = thumbnailUrl,
        itemCount = itemCount,
        syncStatus = syncStatus,
        playlistContentForm = playlistContentForm,
        parentId = parentId,
        children = children.map { it.toDto() },
    )

fun AdminYouTubePlaylistSummary.toDto(): AdminYouTubePlaylistDto =
    AdminYouTubePlaylistDto(
        menuId = menuId,
        playlistId = playlistId,
        menuLabel = menuLabel,
        sourceTitle = sourceTitle,
        slug = slug,
        status = status,
        syncStatus = syncStatus,
        parentId = parentId,
        parentLabel = parentLabel,
        thumbnailUrl = thumbnailUrl,
        itemCount = itemCount,
        playlistContentForm = playlistContentForm,
    )

fun PublicNavigationResponse.toDto(): PublicNavigationResponse = this

fun PublicVideoDetail.toDto(): PublicVideoDetailResponse =
    PublicVideoDetailResponse(
        title = title,
        sourceTitle = sourceTitle,
        playlistId = playlistId,
        slug = slug,
        fullPath = fullPath,
        description = description,
        thumbnailUrl = thumbnailUrl,
        itemCount = itemCount,
        contentForm = contentForm,
        groupLabel = groupLabel,
        siblings = siblings.map { PublicVideoSiblingDto(label = it.label, href = it.href) },
    )

fun PublicResolvedMenuPage.toDto(): PublicResolvedMenuPageResponse =
    PublicResolvedMenuPageResponse(
        menuId = menuId,
        type = type,
        label = label,
        slug = slug,
        fullPath = fullPath,
        parentLabel = parentLabel,
        staticPageKey = staticPageKey,
        boardKey = boardKey,
        redirectTo = redirectTo,
    )

fun YouTubeSyncSummary.toDto(): YouTubeSyncResponse =
    YouTubeSyncResponse(
        status = status,
        totalPlaylists = totalPlaylists,
        createdMenus = createdMenus,
        updatedMenus = updatedMenus,
        archivedMenus = archivedMenus,
        restoredMenus = restoredMenus,
        completedAt = completedAt,
    )
