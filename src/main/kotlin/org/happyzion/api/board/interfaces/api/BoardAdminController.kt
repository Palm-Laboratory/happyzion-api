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
import org.happyzion.api.common.config.AdminProperties
import org.happyzion.api.common.error.ForbiddenException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api/v1/admin/boards")
class BoardAdminController(
    private val boardAdminService: BoardAdminService,
    private val adminProperties: AdminProperties,
) {
    @GetMapping
    fun listBoards(
        @RequestHeader("X-Admin-Key", required = false) adminKey: String?,
        @RequestHeader("X-Admin-Actor-Id") actorId: Long,
    ): BoardAdminListBoardsResponse {
        validateAdminKey(adminKey)

        return BoardAdminListBoardsResponse(
            boards = boardAdminService.listBoards(actorId).map { it.toResponse() },
        )
    }

    @GetMapping("/{slug}/posts")
    fun listPosts(
        @RequestHeader("X-Admin-Key", required = false) adminKey: String?,
        @RequestHeader("X-Admin-Actor-Id") actorId: Long,
        @PathVariable slug: String,
        @RequestParam(required = false) menuId: Long? = null,
        @RequestParam(required = false, defaultValue = "0") page: Int = 0,
        @RequestParam(required = false, defaultValue = "20") size: Int = 20,
        @RequestParam(required = false) title: String? = null,
    ): BoardAdminListPostsResponse {
        validateAdminKey(adminKey)
        val result = boardAdminService.listPosts(actorId, slug, menuId, page, size, title)
        return BoardAdminListPostsResponse(
            posts = result.posts.map { it.toResponse() },
            hasNext = result.hasNext,
        )
    }

    @GetMapping("/{slug}/posts/{postId}")
    fun getPost(
        @RequestHeader("X-Admin-Key", required = false) adminKey: String?,
        @RequestHeader("X-Admin-Actor-Id") actorId: Long,
        @PathVariable slug: String,
        @PathVariable postId: Long,
        @RequestParam(required = false) menuId: Long? = null,
    ): BoardAdminPostDetailResponse {
        validateAdminKey(adminKey)

        return boardAdminService.getPost(actorId, slug, postId, menuId).toResponse()
    }

    @PostMapping("/{slug}/posts")
    fun createPost(
        @RequestHeader("X-Admin-Key", required = false) adminKey: String?,
        @RequestHeader("X-Admin-Actor-Id") actorId: Long,
        @PathVariable slug: String,
        @RequestBody request: BoardPostSaveRequest,
    ): BoardAdminPostSaveResponse {
        validateAdminKey(adminKey)

        return boardAdminService.createPost(
            actorId = actorId,
            boardSlug = slug,
            command = request.toCommand(),
        ).toResponse()
    }

    @PutMapping("/{slug}/posts/{postId}")
    fun updatePost(
        @RequestHeader("X-Admin-Key", required = false) adminKey: String?,
        @RequestHeader("X-Admin-Actor-Id") actorId: Long,
        @PathVariable slug: String,
        @PathVariable postId: Long,
        @RequestBody request: BoardPostSaveRequest,
    ): BoardAdminPostSaveResponse {
        validateAdminKey(adminKey)

        return boardAdminService.updatePost(
            actorId = actorId,
            boardSlug = slug,
            postId = postId,
            command = request.toCommand(),
        ).toResponse()
    }

    @DeleteMapping("/{slug}/posts/{postId}")
    fun deletePost(
        @RequestHeader("X-Admin-Key", required = false) adminKey: String?,
        @RequestHeader("X-Admin-Actor-Id") actorId: Long,
        @PathVariable slug: String,
        @PathVariable postId: Long,
        @RequestParam(required = false) menuId: Long? = null,
    ) {
        validateAdminKey(adminKey)
        boardAdminService.deletePost(actorId, slug, postId, menuId)
    }

    private fun validateAdminKey(adminKey: String?) {
        val configuredKey = adminProperties.syncKey.trim()
        if (configuredKey.isBlank()) {
            throw IllegalStateException("ADMIN_SYNC_KEY is not configured.")
        }

        if (adminKey.isNullOrBlank() || adminKey != configuredKey) {
            throw ForbiddenException("관리자 키가 올바르지 않습니다.")
        }
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
