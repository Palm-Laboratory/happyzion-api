package org.happyzion.api.board.application

import org.happyzion.api.board.domain.PostAssetKind
import java.time.OffsetDateTime

data class PublicBoardPostListResult(
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val hasNext: Boolean,
    val posts: List<PublicBoardPostSummary>,
)

data class PublicBoardPostSummary(
    val id: Long?,
    val boardId: Long,
    val menuId: Long = boardId,
    val title: String,
    val authorName: String,
    val viewCount: Long,
    val contentHtml: String?,
    val hasInlineImage: Boolean,
    val hasVideoEmbed: Boolean,
    val hasAttachments: Boolean,
    val isPinned: Boolean,
    val publishedAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class PublicBoardPostDetail(
    val id: Long?,
    val boardId: Long,
    val menuId: Long = boardId,
    val title: String,
    val authorName: String,
    val viewCount: Long,
    val contentJson: String,
    val contentHtml: String?,
    val isPinned: Boolean,
    val publishedAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val assets: List<PublicBoardPostAsset>,
    val previousPost: PublicBoardAdjacentPost? = null,
    val nextPost: PublicBoardAdjacentPost? = null,
)

data class PublicBoardAdjacentPost(
    val id: Long,
    val title: String,
)

data class PublicBoardPostAsset(
    val id: Long?,
    val kind: PostAssetKind,
    val originalFilename: String,
    val storedPath: String,
    val publicUrl: String,
    val mimeType: String?,
    val byteSize: Long,
    val width: Int?,
    val height: Int?,
    val sortOrder: Int,
)
