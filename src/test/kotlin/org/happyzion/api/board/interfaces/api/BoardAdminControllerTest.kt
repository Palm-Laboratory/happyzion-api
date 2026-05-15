package org.happyzion.api.board.interfaces.api

import org.happyzion.api.board.application.BoardAdminBoardSummary
import org.happyzion.api.board.application.BoardAdminPostAsset
import org.happyzion.api.board.application.BoardAdminPostDetail
import org.happyzion.api.board.application.BoardAdminPostSaveResult
import org.happyzion.api.board.application.BoardAdminPostSummary
import org.happyzion.api.board.application.BoardAdminPostsPage
import org.happyzion.api.board.application.BoardAdminService
import org.happyzion.api.board.application.BoardPostSaveCommand
import org.happyzion.api.board.domain.BoardType
import org.happyzion.api.board.domain.PostAssetKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime

class BoardAdminControllerTest {

    private val boardAdminService: BoardAdminService = mock()

    @Test
    fun `list boards delegates to board admin service`() {
        val controller = controller()
        whenever(boardAdminService.listBoards(42L)).thenReturn(
            listOf(
                BoardAdminBoardSummary(
                    id = 1L,
                    slug = "notice",
                    title = "공지사항",
                    type = BoardType.NOTICE,
                    description = "교회 공지",
                )
            )
        )

        val response = controller.listBoards(actorId = 42L)

        assertThat(response.boards).hasSize(1)
        assertThat(response.boards[0].slug).isEqualTo("notice")
        assertThat(response.boards[0].title).isEqualTo("공지사항")
        verify(boardAdminService).listBoards(42L)
    }

    @Test
    fun `list posts delegates to board admin service`() {
        val controller = controller()
        val createdAt = OffsetDateTime.parse("2026-04-20T10:15:30+09:00")
        val updatedAt = createdAt.plusHours(1)
        whenever(boardAdminService.listPosts(42L, "notice")).thenReturn(
            BoardAdminPostsPage(
                posts = listOf(
                    BoardAdminPostSummary(
                        id = 11L,
                        boardId = 1L,
                        title = "주일 예배 안내",
                        isPublic = true,
                        isPinned = true,
                        authorId = 42L,
                        authorName = "관리자",
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                    )
                ),
                hasNext = false,
            )
        )

        val response = controller.listPosts(
            actorId = 42L,
            slug = "notice",
        )

        assertThat(response.posts).hasSize(1)
        assertThat(response.posts[0].id).isEqualTo(11L)
        assertThat(response.posts[0].title).isEqualTo("주일 예배 안내")
        assertThat(response.posts[0].isPinned).isTrue()
        assertThat(response.hasNext).isFalse()
        verify(boardAdminService).listPosts(42L, "notice")
    }

    @Test
    fun `get post delegates to board admin service`() {
        val controller = controller()
        val createdAt = OffsetDateTime.parse("2026-04-20T10:15:30+09:00")
        val updatedAt = createdAt.plusHours(1)
        whenever(boardAdminService.getPost(42L, "notice", 11L)).thenReturn(
            BoardAdminPostDetail(
                id = 11L,
                boardId = 1L,
                title = "주일 예배 안내",
                contentJson = """{"type":"doc","content":[]}""",
                contentHtml = "<p>주일 예배 안내</p>",
                isPublic = true,
                isPinned = true,
                authorId = 42L,
                createdAt = createdAt,
                updatedAt = updatedAt,
                assets = listOf(
                    BoardAdminPostAsset(
                        id = 100L,
                        kind = PostAssetKind.INLINE_IMAGE,
                        originalFilename = "worship.png",
                        storedPath = "uploads/worship.png",
                        mimeType = "image/png",
                        byteSize = 123L,
                        width = 640,
                        height = 480,
                        sortOrder = 0,
                    ),
                    BoardAdminPostAsset(
                        id = 101L,
                        kind = PostAssetKind.FILE_ATTACHMENT,
                        originalFilename = "bulletin.pdf",
                        storedPath = "uploads/bulletin.pdf",
                        mimeType = "application/pdf",
                        byteSize = 456L,
                        width = null,
                        height = null,
                        sortOrder = 1,
                    ),
                ),
            )
        )

        val response = controller.getPost(
            actorId = 42L,
            slug = "notice",
            postId = 11L,
        )

        assertThat(response.id).isEqualTo(11L)
        assertThat(response.title).isEqualTo("주일 예배 안내")
        assertThat(response.contentJson).isEqualTo("""{"type":"doc","content":[]}""")
        assertThat(response.contentHtml).isEqualTo("<p>주일 예배 안내</p>")
        assertThat(response.isPinned).isTrue()
        assertThat(response.assets).hasSize(2)
        assertThat(response.assets.map { it.id }).containsExactly(100L, 101L)
        assertThat(response.assets.map { it.kind }).containsExactly(
            PostAssetKind.INLINE_IMAGE,
            PostAssetKind.FILE_ATTACHMENT,
        )
        assertThat(response.assets.map { it.originalFilename }).containsExactly("worship.png", "bulletin.pdf")
        assertThat(response.assets.map { it.storedPath }).containsExactly("uploads/worship.png", "uploads/bulletin.pdf")
        assertThat(response.assets.map { it.mimeType }).containsExactly("image/png", "application/pdf")
        assertThat(response.assets.map { it.byteSize }).containsExactly(123L, 456L)
        assertThat(response.assets.map { it.width }).containsExactly(640, null)
        assertThat(response.assets.map { it.height }).containsExactly(480, null)
        assertThat(response.assets.map { it.sortOrder }).containsExactly(0, 1)
        verify(boardAdminService).getPost(42L, "notice", 11L)
    }

    @Test
    fun `create post maps request to command and delegates to board admin service`() {
        val controller = controller()
        whenever(
            boardAdminService.createPost(
                actorId = eq(42L),
                boardSlug = eq("notice"),
                command = org.mockito.kotlin.any(),
                menuId = org.mockito.kotlin.isNull(),
            )
        ).thenReturn(BoardAdminPostSaveResult(id = 11L))

        val response = controller.createPost(
            actorId = 42L,
            slug = "notice",
            request = saveRequest(),
        )

        assertThat(response.id).isEqualTo(11L)
        val commandCaptor = argumentCaptor<BoardPostSaveCommand>()
        verify(boardAdminService).createPost(
            actorId = eq(42L),
            boardSlug = eq("notice"),
            command = commandCaptor.capture(),
            menuId = org.mockito.kotlin.isNull(),
        )
        assertThat(commandCaptor.firstValue.title).isEqualTo("주일 예배 안내")
        assertThat(commandCaptor.firstValue.contentJson).isEqualTo("""{"type":"doc","content":[]}""")
        assertThat(commandCaptor.firstValue.contentHtml).isEqualTo("<p>주일 예배 안내</p>")
        assertThat(commandCaptor.firstValue.isPublic).isFalse()
        assertThat(commandCaptor.firstValue.isPinned).isTrue()
        assertThat(commandCaptor.firstValue.assetIds).containsExactly(100L, 101L)
    }

    @Test
    fun `update post maps request to command and delegates to board admin service`() {
        val controller = controller()
        whenever(
            boardAdminService.updatePost(
                actorId = eq(42L),
                boardSlug = eq("notice"),
                postId = eq(11L),
                command = org.mockito.kotlin.any(),
                menuId = org.mockito.kotlin.isNull(),
            )
        ).thenReturn(BoardAdminPostSaveResult(id = 11L))

        val response = controller.updatePost(
            actorId = 42L,
            slug = "notice",
            postId = 11L,
            request = saveRequest(),
        )

        assertThat(response.id).isEqualTo(11L)
        val commandCaptor = argumentCaptor<BoardPostSaveCommand>()
        verify(boardAdminService).updatePost(
            actorId = eq(42L),
            boardSlug = eq("notice"),
            postId = eq(11L),
            command = commandCaptor.capture(),
            menuId = org.mockito.kotlin.isNull(),
        )
        assertThat(commandCaptor.firstValue.title).isEqualTo("주일 예배 안내")
        assertThat(commandCaptor.firstValue.contentJson).isEqualTo("""{"type":"doc","content":[]}""")
        assertThat(commandCaptor.firstValue.contentHtml).isEqualTo("<p>주일 예배 안내</p>")
        assertThat(commandCaptor.firstValue.isPublic).isFalse()
        assertThat(commandCaptor.firstValue.isPinned).isTrue()
        assertThat(commandCaptor.firstValue.assetIds).containsExactly(100L, 101L)
    }

    @Test
    fun `delete post delegates to board admin service`() {
        val controller = controller()

        controller.deletePost(
            actorId = 42L,
            slug = "notice",
            postId = 11L,
        )

        verify(boardAdminService).deletePost(42L, "notice", 11L)
    }

    private fun controller(): BoardAdminController =
        BoardAdminController(boardAdminService = boardAdminService)

    private fun saveRequest(): BoardPostSaveRequest =
        BoardPostSaveRequest(
            title = "주일 예배 안내",
            contentJson = """{"type":"doc","content":[]}""",
            contentHtml = "<p>주일 예배 안내</p>",
            isPublic = false,
            isPinned = true,
            assetIds = listOf(100L, 101L),
        )
}
