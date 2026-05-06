package org.happyzion.api.board.interfaces.api

import org.happyzion.api.board.application.PublicBoardPostAsset
import org.happyzion.api.board.application.PublicBoardAdjacentPost
import org.happyzion.api.board.application.PublicBoardPostDetail
import org.happyzion.api.board.application.PublicBoardPostListResult
import org.happyzion.api.board.application.PublicBoardPostSummary
import org.happyzion.api.board.application.PublicBoardService
import org.happyzion.api.board.domain.PostAssetKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime

class PublicBoardControllerTest {

    private val publicBoardService: PublicBoardService = mock()

    @Test
    fun `list posts delegates to public board service and returns paged posts`() {
        val controller = controller()
        val createdAt = OffsetDateTime.parse("2026-04-20T10:15:30+09:00")
        val updatedAt = createdAt.plusHours(1)
        whenever(publicBoardService.listPosts("notice", 1, 10, null, "예배")).thenReturn(
            PublicBoardPostListResult(
                page = 1,
                size = 10,
                totalElements = 21L,
                hasNext = true,
                posts = listOf(
                    PublicBoardPostSummary(
                        id = 11L,
                        boardId = 1L,
                        title = "주일 예배 안내",
                        authorName = "관리자",
                        viewCount = 12L,
                        contentHtml = "<p>주일 예배 안내</p>",
                        hasInlineImage = true,
                        hasVideoEmbed = false,
                        hasAttachments = true,
                        isPinned = true,
                        publishedAt = createdAt,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                    ),
                    PublicBoardPostSummary(
                        id = 12L,
                        boardId = 1L,
                        title = "수요 예배 안내",
                        authorName = "운영자",
                        viewCount = 3L,
                        contentHtml = null,
                        hasInlineImage = false,
                        hasVideoEmbed = true,
                        hasAttachments = false,
                        isPinned = false,
                        publishedAt = null,
                        createdAt = createdAt.plusDays(1),
                        updatedAt = updatedAt.plusDays(1),
                    ),
                ),
            )
        )

        val response = controller.listPosts(
            slug = "notice",
            page = 1,
            size = 10,
            title = "예배",
        )

        assertThat(response.page).isEqualTo(1)
        assertThat(response.size).isEqualTo(10)
        assertThat(response.totalElements).isEqualTo(21L)
        assertThat(response.hasNext).isTrue()
        assertThat(response.posts).hasSize(2)
        assertThat(response.posts.map { it.id }).containsExactly(11L, 12L)
        assertThat(response.posts.map { it.boardId }).containsExactly(1L, 1L)
        assertThat(response.posts.map { it.title }).containsExactly("주일 예배 안내", "수요 예배 안내")
        assertThat(response.posts.map { it.authorName }).containsExactly("관리자", "운영자")
        assertThat(response.posts.map { it.viewCount }).containsExactly(12L, 3L)
        assertThat(response.posts.map { it.contentHtml }).containsExactly("<p>주일 예배 안내</p>", null)
        assertThat(response.posts.map { it.hasInlineImage }).containsExactly(true, false)
        assertThat(response.posts.map { it.hasVideoEmbed }).containsExactly(false, true)
        assertThat(response.posts.map { it.hasAttachments }).containsExactly(true, false)
        assertThat(response.posts.map { it.isPinned }).containsExactly(true, false)
        assertThat(response.posts.map { it.publishedAt }).containsExactly(createdAt, null)
        assertThat(response.posts.map { it.createdAt }).containsExactly(createdAt, createdAt.plusDays(1))
        assertThat(response.posts.map { it.updatedAt }).containsExactly(updatedAt, updatedAt.plusDays(1))
        verify(publicBoardService).listPosts("notice", 1, 10, null, "예배")
    }

    @Test
    fun `get post delegates to public board service and returns post detail`() {
        val controller = controller()
        val createdAt = OffsetDateTime.parse("2026-04-20T10:15:30+09:00")
        val updatedAt = createdAt.plusHours(1)
        whenever(publicBoardService.getPost("notice", 11L)).thenReturn(
            PublicBoardPostDetail(
                id = 11L,
                boardId = 1L,
                title = "주일 예배 안내",
                authorName = "관리자",
                viewCount = 34L,
                contentJson = """{"type":"doc","content":[]}""",
                contentHtml = "<p>주일 예배 안내</p>",
                isPinned = true,
                publishedAt = createdAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
                assets = listOf(
                    PublicBoardPostAsset(
                        id = 100L,
                        kind = PostAssetKind.INLINE_IMAGE,
                        originalFilename = "worship.png",
                        storedPath = "uploads/worship.png",
                        publicUrl = "https://cdn.example.com/uploads/worship.png",
                        mimeType = "image/png",
                        byteSize = 123L,
                        width = 640,
                        height = 480,
                        sortOrder = 0,
                    ),
                    PublicBoardPostAsset(
                        id = 101L,
                        kind = PostAssetKind.FILE_ATTACHMENT,
                        originalFilename = "bulletin.pdf",
                        storedPath = "uploads/bulletin.pdf",
                        publicUrl = "https://cdn.example.com/uploads/bulletin.pdf",
                        mimeType = "application/pdf",
                        byteSize = 456L,
                        width = null,
                        height = null,
                        sortOrder = 1,
                    ),
                ),
                previousPost = PublicBoardAdjacentPost(
                    id = 10L,
                    title = "이전 게시글",
                ),
                nextPost = PublicBoardAdjacentPost(
                    id = 12L,
                    title = "다음 게시글",
                ),
            )
        )

        val response = controller.getPost(
            slug = "notice",
            postId = 11L,
        )

        assertThat(response.id).isEqualTo(11L)
        assertThat(response.boardId).isEqualTo(1L)
        assertThat(response.title).isEqualTo("주일 예배 안내")
        assertThat(response.authorName).isEqualTo("관리자")
        assertThat(response.viewCount).isEqualTo(34L)
        assertThat(response.contentJson).isEqualTo("""{"type":"doc","content":[]}""")
        assertThat(response.contentHtml).isEqualTo("<p>주일 예배 안내</p>")
        assertThat(response.isPinned).isTrue()
        assertThat(response.publishedAt).isEqualTo(createdAt)
        assertThat(response.createdAt).isEqualTo(createdAt)
        assertThat(response.updatedAt).isEqualTo(updatedAt)
        assertThat(response.assets).hasSize(2)
        assertThat(response.assets.map { it.id }).containsExactly(100L, 101L)
        assertThat(response.assets.map { it.kind }).containsExactly(
            PostAssetKind.INLINE_IMAGE,
            PostAssetKind.FILE_ATTACHMENT,
        )
        assertThat(response.assets.map { it.originalFilename }).containsExactly("worship.png", "bulletin.pdf")
        assertThat(response.assets.map { it.storedPath }).containsExactly("uploads/worship.png", "uploads/bulletin.pdf")
        assertThat(response.assets.map { it.publicUrl }).containsExactly(
            "https://cdn.example.com/uploads/worship.png",
            "https://cdn.example.com/uploads/bulletin.pdf",
        )
        assertThat(response.assets.map { it.mimeType }).containsExactly("image/png", "application/pdf")
        assertThat(response.assets.map { it.byteSize }).containsExactly(123L, 456L)
        assertThat(response.assets.map { it.width }).containsExactly(640, null)
        assertThat(response.assets.map { it.height }).containsExactly(480, null)
        assertThat(response.assets.map { it.sortOrder }).containsExactly(0, 1)
        assertThat(response.previousPost?.id).isEqualTo(10L)
        assertThat(response.previousPost?.title).isEqualTo("이전 게시글")
        assertThat(response.nextPost?.id).isEqualTo(12L)
        assertThat(response.nextPost?.title).isEqualTo("다음 게시글")
        verify(publicBoardService).getPost("notice", 11L)
    }

    private fun controller(): PublicBoardController =
        PublicBoardController(publicBoardService = publicBoardService)
}
