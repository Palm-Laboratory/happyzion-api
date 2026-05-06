package org.happyzion.api.board.application

import org.happyzion.api.board.domain.BoardType
import org.happyzion.api.board.domain.PostAssetKind
import java.time.OffsetDateTime

data class BoardPostSaveCommand(
    val menuId: Long? = null,
    val title: String,
    val contentJson: String,
    val contentHtml: String? = null,
    val isPublic: Boolean = true,
    val isPinned: Boolean = false,
    val assetIds: List<Long> = emptyList(),
)

data class BoardAdminBoardSummary(
    val id: Long?,
    val slug: String,
    val title: String,
    val type: BoardType,
    val description: String? = null,
)

data class BoardAdminPostSummary(
    val id: Long?,
    val boardId: Long,
    val menuId: Long = boardId,
    val title: String,
    val isPublic: Boolean,
    val isPinned: Boolean,
    val authorId: Long,
    val authorName: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class BoardAdminPostDetail(
    val id: Long?,
    val boardId: Long,
    val menuId: Long = boardId,
    val title: String,
    val contentJson: String,
    val contentHtml: String?,
    val isPublic: Boolean,
    val isPinned: Boolean,
    val authorId: Long,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val assets: List<BoardAdminPostAsset> = emptyList(),
)

data class BoardAdminPostAsset(
    val id: Long?,
    val kind: PostAssetKind,
    val originalFilename: String,
    val storedPath: String,
    val mimeType: String?,
    val byteSize: Long,
    val width: Int?,
    val height: Int?,
    val sortOrder: Int,
)

data class BoardAdminPostsPage(
    val posts: List<BoardAdminPostSummary>,
    val hasNext: Boolean,
)

data class BoardAdminPostSaveResult(
    val id: Long,
)
