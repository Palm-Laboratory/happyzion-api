package org.happyzion.api.board.interfaces.api

import org.happyzion.api.board.application.BoardAdminBoardSummary
import org.happyzion.api.board.application.BoardAdminPostDetail
import org.happyzion.api.board.application.BoardAdminPostSaveResult
import org.happyzion.api.board.application.BoardAdminPostSummary
import org.happyzion.api.board.application.BoardAdminPostsPage
import org.happyzion.api.board.application.BoardAdminService
import org.happyzion.api.board.application.BoardPostSaveCommand
import org.happyzion.api.board.domain.BoardType
import org.happyzion.api.board.domain.PostAssetKind
import org.happyzion.api.common.security.AdminKeyRequired
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@AdminKeyRequired
@RestController
@RequestMapping("/api/v1/admin/boards")
class BoardAdminController(
    private val boardAdminService: BoardAdminService,
) {
    @GetMapping
    fun listBoards(
        @RequestAttribute("adminAccountId") actorId: Long,
    ): BoardAdminListBoardsResponse {
        return BoardAdminListBoardsResponse(
            boards = boardAdminService.listBoards(actorId).map { it.toResponse() },
        )
    }

    @GetMapping("/{slug}/posts")
    fun listPosts(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable slug: String,
        @RequestParam(required = false) menuId: Long? = null,
        @RequestParam(required = false, defaultValue = "0") page: Int = 0,
        @RequestParam(required = false, defaultValue = "20") size: Int = 20,
        @RequestParam(required = false) title: String? = null,
    ): BoardAdminListPostsResponse {
        val result = boardAdminService.listPosts(actorId, slug, menuId, page, size, title)
        return BoardAdminListPostsResponse(
            posts = result.posts.map { it.toResponse() },
            hasNext = result.hasNext,
        )
    }

    @GetMapping("/{slug}/posts/{postId}")
    fun getPost(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable slug: String,
        @PathVariable postId: Long,
        @RequestParam(required = false) menuId: Long? = null,
    ): BoardAdminPostDetailResponse {
        return boardAdminService.getPost(actorId, slug, postId, menuId).toResponse()
    }

    @PostMapping("/{slug}/posts")
    fun createPost(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable slug: String,
        @RequestBody request: BoardPostSaveRequest,
    ): BoardAdminPostSaveResponse {
        return boardAdminService.createPost(
            actorId = actorId,
            boardSlug = slug,
            command = request.toCommand(),
        ).toResponse()
    }

    @PutMapping("/{slug}/posts/{postId}")
    fun updatePost(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable slug: String,
        @PathVariable postId: Long,
        @RequestBody request: BoardPostSaveRequest,
    ): BoardAdminPostSaveResponse {
        return boardAdminService.updatePost(
            actorId = actorId,
            boardSlug = slug,
            postId = postId,
            command = request.toCommand(),
        ).toResponse()
    }

    @DeleteMapping("/{slug}/posts/{postId}")
    fun deletePost(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable slug: String,
        @PathVariable postId: Long,
        @RequestParam(required = false) menuId: Long? = null,
    ) {
        boardAdminService.deletePost(actorId, slug, postId, menuId)
    }
}

data class BoardPostSaveRequest(
    val menuId: Long? = null,
    val title: String,
    val contentJson: String,
    val contentHtml: String? = null,
    val isPublic: Boolean = true,
    val isPinned: Boolean = false,
    val assetIds: List<Long> = emptyList(),
) {
    fun toCommand(): BoardPostSaveCommand =
        BoardPostSaveCommand(
            menuId = menuId,
            title = title,
            contentJson = contentJson,
            contentHtml = contentHtml,
            isPublic = isPublic,
            isPinned = isPinned,
            assetIds = assetIds,
        )
}

data class BoardAdminListBoardsResponse(
    val boards: List<BoardAdminBoardResponse>,
)

data class BoardAdminBoardResponse(
    val id: Long?,
    val slug: String,
    val title: String,
    val type: BoardType,
    val description: String?,
)

data class BoardAdminListPostsResponse(
    val posts: List<BoardAdminPostSummaryResponse>,
    val hasNext: Boolean = false,
)

data class BoardAdminPostSummaryResponse(
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

data class BoardAdminPostDetailResponse(
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
    val assets: List<BoardAdminPostAssetResponse>,
)

data class BoardAdminPostAssetResponse(
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

data class BoardAdminPostSaveResponse(
    val id: Long,
)

private fun BoardAdminBoardSummary.toResponse(): BoardAdminBoardResponse =
    BoardAdminBoardResponse(
        id = id,
        slug = slug,
        title = title,
        type = type,
        description = description,
    )

private fun BoardAdminPostSummary.toResponse(): BoardAdminPostSummaryResponse =
    BoardAdminPostSummaryResponse(
        id = id,
        boardId = boardId,
        menuId = menuId,
        title = title,
        isPublic = isPublic,
        isPinned = isPinned,
        authorId = authorId,
        authorName = authorName,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun BoardAdminPostDetail.toResponse(): BoardAdminPostDetailResponse =
    BoardAdminPostDetailResponse(
        id = id,
        boardId = boardId,
        menuId = menuId,
        title = title,
        contentJson = contentJson,
        contentHtml = contentHtml,
        isPublic = isPublic,
        isPinned = isPinned,
        authorId = authorId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        assets = assets.map { asset ->
            BoardAdminPostAssetResponse(
                id = asset.id,
                kind = asset.kind,
                originalFilename = asset.originalFilename,
                storedPath = asset.storedPath,
                mimeType = asset.mimeType,
                byteSize = asset.byteSize,
                width = asset.width,
                height = asset.height,
                sortOrder = asset.sortOrder,
            )
        },
    )

private fun BoardAdminPostSaveResult.toResponse(): BoardAdminPostSaveResponse =
    BoardAdminPostSaveResponse(id = id)
