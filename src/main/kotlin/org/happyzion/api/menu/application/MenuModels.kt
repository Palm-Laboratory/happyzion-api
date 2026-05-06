package org.happyzion.api.menu.application

import org.happyzion.api.board.domain.BoardType
import org.happyzion.api.menu.domain.MenuStatus
import org.happyzion.api.menu.domain.MenuType
import org.happyzion.api.youtube.domain.YouTubeContentForm
import org.happyzion.api.youtube.domain.YouTubeSyncStatus

data class MenuTreeNodeInput(
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
    val children: List<MenuTreeNodeInput> = emptyList(),
)

data class MenuTreeNode(
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
    val playlistTitle: String? = null,
    val playlistSourceTitle: String? = null,
    val thumbnailUrl: String? = null,
    val itemCount: Int? = null,
    val syncStatus: YouTubeSyncStatus? = null,
    val playlistContentForm: YouTubeContentForm? = null,
    val parentId: Long? = null,
    val children: List<MenuTreeNode> = emptyList(),
)

data class AdminMenuSnapshot(
    val items: List<MenuTreeNode>,
)

data class AdminYouTubePlaylistSummary(
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

data class YouTubeSyncSummary(
    val status: String,
    val totalPlaylists: Int,
    val createdMenus: Int,
    val updatedMenus: Int,
    val archivedMenus: Int,
    val restoredMenus: Int,
    val completedAt: String,
)

data class VideoSiblingLink(
    val label: String,
    val href: String,
)

data class PublicVideoDetail(
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
    val siblings: List<VideoSiblingLink>,
)

data class PublicResolvedMenuPage(
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
