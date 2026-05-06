package org.happyzion.api.board.interfaces.api

import org.happyzion.api.board.application.PublicBoardPostAsset
import org.happyzion.api.board.application.PublicBoardAdjacentPost
import org.happyzion.api.board.application.PublicBoardPostDetail
import org.happyzion.api.board.application.PublicBoardPostListResult
import org.happyzion.api.board.application.PublicBoardPostSummary
import org.happyzion.api.board.application.PublicBoardService
import org.happyzion.api.board.domain.PostAssetKind
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api/v1/public/boards")
class PublicBoardController(
    private val publicBoardService: PublicBoardService,
) {
    @GetMapping("/{slug}/posts")
    fun listPosts(
        @PathVariable slug: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) menuId: Long? = null,
        @RequestParam(required = false) title: String? = null,
    ): PublicBoardListPostsResponse =
        publicBoardService.listPosts(slug, page, size, menuId, title).toResponse()

    @GetMapping("/{slug}/posts/{postId}")
    fun getPost(
        @PathVariable slug: String,
        @PathVariable postId: Long,
        @RequestParam(required = false) menuId: Long? = null,
    ): PublicBoardPostDetailResponse =
        publicBoardService.getPost(slug, postId, menuId).toResponse()
}

data class PublicBoardListPostsResponse(
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val hasNext: Boolean,
    val posts: List<PublicBoardPostSummaryResponse>,
)

data class PublicBoardPostSummaryResponse(
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

data class PublicBoardPostDetailResponse(
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
    val assets: List<PublicBoardPostAssetResponse>,
    val previousPost: PublicBoardAdjacentPostResponse? = null,
    val nextPost: PublicBoardAdjacentPostResponse? = null,
)

data class PublicBoardAdjacentPostResponse(
    val id: Long,
    val title: String,
)

data class PublicBoardPostAssetResponse(
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

private fun PublicBoardPostListResult.toResponse(): PublicBoardListPostsResponse =
    PublicBoardListPostsResponse(
        page = page,
        size = size,
        totalElements = totalElements,
        hasNext = hasNext,
        posts = posts.map { it.toResponse() },
    )

private fun PublicBoardPostSummary.toResponse(): PublicBoardPostSummaryResponse =
    PublicBoardPostSummaryResponse(
        id = id,
        boardId = boardId,
        menuId = menuId,
        title = title,
        authorName = authorName,
        viewCount = viewCount,
        contentHtml = contentHtml,
        hasInlineImage = hasInlineImage,
        hasVideoEmbed = hasVideoEmbed,
        hasAttachments = hasAttachments,
        isPinned = isPinned,
        publishedAt = publishedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun PublicBoardPostDetail.toResponse(): PublicBoardPostDetailResponse =
    PublicBoardPostDetailResponse(
        id = id,
        boardId = boardId,
        menuId = menuId,
        title = title,
        authorName = authorName,
        viewCount = viewCount,
        contentJson = contentJson,
        contentHtml = contentHtml,
        isPinned = isPinned,
        publishedAt = publishedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        assets = assets.map { it.toResponse() },
        previousPost = previousPost?.toResponse(),
        nextPost = nextPost?.toResponse(),
    )

private fun PublicBoardPostAsset.toResponse(): PublicBoardPostAssetResponse =
    PublicBoardPostAssetResponse(
        id = id,
        kind = kind,
        originalFilename = originalFilename,
        storedPath = storedPath,
        publicUrl = publicUrl,
        mimeType = mimeType,
        byteSize = byteSize,
        width = width,
        height = height,
        sortOrder = sortOrder,
    )

private fun PublicBoardAdjacentPost.toResponse(): PublicBoardAdjacentPostResponse =
    PublicBoardAdjacentPostResponse(
        id = id,
        title = title,
    )
