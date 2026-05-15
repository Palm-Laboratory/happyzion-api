package org.happyzion.api.board.application

import org.happyzion.api.board.domain.Board
import org.happyzion.api.board.domain.BoardType
import org.happyzion.api.board.domain.Post
import org.happyzion.api.board.domain.PostAsset
import org.happyzion.api.board.domain.PostAssetKind
import org.happyzion.api.board.infrastructure.persistence.BoardRepository
import org.happyzion.api.board.infrastructure.persistence.PostAssetRepository
import org.happyzion.api.board.infrastructure.persistence.PostRepository
import org.happyzion.api.adminaccount.domain.AdminAccount
import org.happyzion.api.adminaccount.domain.AdminAccountRole
import org.happyzion.api.adminaccount.infrastructure.persistence.AdminAccountRepository
import org.happyzion.api.common.config.UploadProperties
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.menu.domain.MenuStatus
import org.happyzion.api.menu.domain.MenuType
import org.happyzion.api.menu.infrastructure.persistence.MenuItemRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class PublicBoardServiceTest {

    private val boardRepository: BoardRepository = mock()
    private val postRepository: PostRepository = mock()
    private val postAssetRepository: PostAssetRepository = mock()
    private val adminAccountRepository: AdminAccountRepository = mock()
    private val menuItemRepository: MenuItemRepository = mock()
    private val uploadProperties: UploadProperties = mock()

    private val service = PublicBoardService(
        adminAccountRepository = adminAccountRepository,
        boardRepository = boardRepository,
        postRepository = postRepository,
        postAssetRepository = postAssetRepository,
        menuItemRepository = menuItemRepository,
        uploadProperties = uploadProperties,
    )

    @Test
    fun `list posts throws not found when board slug does not exist`() {
        whenever(boardRepository.findBySlug("missing")).thenReturn(null)

        assertThrows<NotFoundException> {
            service.listPosts(boardSlug = "missing", page = 0, size = 20)
        }

        verify(menuItemRepository, never()).existsByTypeAndStatusAndBoardKey(
            eq(MenuType.BOARD),
            eq(MenuStatus.PUBLISHED),
            eq("missing"),
        )
        verify(postRepository, never()).findAllByBoardIdAndIsPublicTrueOrderByIsPinnedDescCreatedAtDescIdDesc(
            eq(10L),
            eq(PageRequest.of(0, 20)),
        )
    }

    @Test
    fun `list posts throws not found when board is not connected to a published board menu item`() {
        val board = board(slug = "notice")
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(
            menuItemRepository.existsByTypeAndStatusAndBoardKey(
                MenuType.BOARD,
                MenuStatus.PUBLISHED,
                "notice",
            )
        ).thenReturn(false)

        assertThrows<NotFoundException> {
            service.listPosts(boardSlug = "notice", page = 0, size = 20)
        }

        verify(postRepository, never()).findAllByBoardIdAndIsPublicTrueOrderByIsPinnedDescCreatedAtDescIdDesc(
            eq(board.id!!),
            eq(PageRequest.of(0, 20)),
        )
    }

    @Test
    fun `list posts returns only public posts for a published board menu connection`() {
        val board = board(slug = "notice")
        val pageRequest = PageRequest.of(1, 2)
        val publicPost = post(id = 101L, boardId = board.id!!, title = "공개 소식")
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(
            menuItemRepository.existsByTypeAndStatusAndBoardKey(
                MenuType.BOARD,
                MenuStatus.PUBLISHED,
                "notice",
            )
        ).thenReturn(true)
        whenever(
            postRepository.findAllByBoardIdAndIsPublicTrueOrderByIsPinnedDescCreatedAtDescIdDesc(
                board.id!!,
                pageRequest,
            )
        ).thenReturn(PageImpl(listOf(publicPost), pageRequest, 3))
        whenever(adminAccountRepository.findAllById(listOf(1L))).thenReturn(listOf(adminAccount()))
        whenever(postAssetRepository.findAllByPostIdIn(listOf(101L))).thenReturn(
            listOf(
                asset(id = 201L, postId = 101L, storedPath = "uploads/notice/image.png", sortOrder = 0),
                asset(
                    id = 202L,
                    postId = 101L,
                    kind = PostAssetKind.FILE_ATTACHMENT,
                    originalFilename = "notice.pdf",
                    storedPath = "uploads/notice/notice.pdf",
                    sortOrder = 1,
                ),
            )
        )

        val result = service.listPosts(boardSlug = "notice", page = 1, size = 2)

        assertThat(result.page).isEqualTo(1)
        assertThat(result.size).isEqualTo(2)
        assertThat(result.totalElements).isEqualTo(3)
        assertThat(result.hasNext).isFalse()
        assertThat(result.posts).hasSize(1)
        assertThat(result.posts[0].id).isEqualTo(101L)
        assertThat(result.posts[0].title).isEqualTo("공개 소식")
        assertThat(result.posts[0].authorName).isEqualTo("관리자")
        assertThat(result.posts[0].viewCount).isZero()
        assertThat(result.posts[0].hasInlineImage).isTrue()
        assertThat(result.posts[0].hasVideoEmbed).isFalse()
        assertThat(result.posts[0].hasAttachments).isTrue()
        verify(postRepository).findAllByBoardIdAndIsPublicTrueOrderByIsPinnedDescCreatedAtDescIdDesc(
            board.id!!,
            pageRequest,
        )
        verify(postRepository, never()).findAllByBoardIdOrderByIsPinnedDescCreatedAtDescIdDesc(board.id!!)
    }

    @Test
    fun `list posts clamps requested page size to public operational maximum`() {
        val board = board(slug = "notice")
        val operationalMaxPageRequest = PageRequest.of(0, 50)
        val unboundedPageRequest = PageRequest.of(0, 500)
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(
            menuItemRepository.existsByTypeAndStatusAndBoardKey(
                MenuType.BOARD,
                MenuStatus.PUBLISHED,
                "notice",
            )
        ).thenReturn(true)
        whenever(
            postRepository.findAllByBoardIdAndIsPublicTrueOrderByIsPinnedDescCreatedAtDescIdDesc(
                board.id!!,
                operationalMaxPageRequest,
            )
        ).thenReturn(PageImpl(emptyList(), operationalMaxPageRequest, 0))
        whenever(
            postRepository.findAllByBoardIdAndIsPublicTrueOrderByIsPinnedDescCreatedAtDescIdDesc(
                board.id!!,
                unboundedPageRequest,
            )
        ).thenReturn(PageImpl(emptyList(), unboundedPageRequest, 0))
        whenever(adminAccountRepository.findAllById(emptyList<Long>())).thenReturn(emptyList())

        val result = service.listPosts(boardSlug = "notice", page = 0, size = 500)

        assertThat(result.size).isEqualTo(50)
        verify(postRepository).findAllByBoardIdAndIsPublicTrueOrderByIsPinnedDescCreatedAtDescIdDesc(
            board.id!!,
            operationalMaxPageRequest,
        )
    }

    @Test
    fun `list posts returns hasNext false on last page and hasNext true when more pages exist`() {
        val board = board(slug = "notice")
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(
            menuItemRepository.existsByTypeAndStatusAndBoardKey(MenuType.BOARD, MenuStatus.PUBLISHED, "notice")
        ).thenReturn(true)

        // 마지막 페이지: page=0, size=3, total=3 → hasNext=false
        whenever(
            postRepository.findAllByBoardIdAndIsPublicTrueOrderByIsPinnedDescCreatedAtDescIdDesc(
                board.id!!,
                PageRequest.of(0, 3),
            )
        ).thenReturn(PageImpl(listOf(post(1L, board.id!!, title = "글1"), post(2L, board.id!!, title = "글2"), post(3L, board.id!!, title = "글3")), PageRequest.of(0, 3), 3))
        whenever(adminAccountRepository.findAllById(listOf(1L))).thenReturn(listOf(adminAccount()))
        whenever(postAssetRepository.findAllByPostIdIn(listOf(1L, 2L, 3L))).thenReturn(emptyList())

        val lastPage = service.listPosts(boardSlug = "notice", page = 0, size = 3)
        assertThat(lastPage.hasNext).isFalse()

        // 다음 페이지 있음: page=0, size=2, total=3 → hasNext=true
        whenever(
            postRepository.findAllByBoardIdAndIsPublicTrueOrderByIsPinnedDescCreatedAtDescIdDesc(
                board.id!!,
                PageRequest.of(0, 2),
            )
        ).thenReturn(PageImpl(listOf(post(1L, board.id!!, title = "글1"), post(2L, board.id!!, title = "글2")), PageRequest.of(0, 2), 3))
        whenever(postAssetRepository.findAllByPostIdIn(listOf(1L, 2L))).thenReturn(emptyList())

        val firstPage = service.listPosts(boardSlug = "notice", page = 0, size = 2)
        assertThat(firstPage.hasNext).isTrue()
    }

    @Test
    fun `list posts receives menu id and returns only public posts under that menu`() {
        val board = board(slug = "notice")
        val pageRequest = PageRequest.of(0, 20)
        val publicPost = post(
            id = 101L,
            boardId = board.id!!,
            menuId = 1001L,
            title = "첫 번째 공지 메뉴 공개 글",
        )
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(
            menuItemRepository.existsByIdAndTypeAndStatusAndBoardKey(
                1001L,
                MenuType.BOARD,
                MenuStatus.PUBLISHED,
                "notice",
            )
        ).thenReturn(true)
        whenever(
            postRepository.findAllByMenuIdAndIsPublicTrueOrderByIsPinnedDescCreatedAtDescIdDesc(
                1001L,
                pageRequest,
            )
        ).thenReturn(PageImpl(listOf(publicPost), pageRequest, 1))
        whenever(adminAccountRepository.findAllById(listOf(1L))).thenReturn(listOf(adminAccount()))
        whenever(postAssetRepository.findAllByPostIdIn(listOf(101L))).thenReturn(emptyList())

        val result = service.listPosts(boardSlug = "notice", menuId = 1001L, page = 0, size = 20)

        assertThat(result.posts).hasSize(1)
        assertThat(result.posts[0].id).isEqualTo(101L)
        verify(postRepository).findAllByMenuIdAndIsPublicTrueOrderByIsPinnedDescCreatedAtDescIdDesc(1001L, pageRequest)
        verify(postRepository, never()).findAllByBoardIdAndIsPublicTrueOrderByIsPinnedDescCreatedAtDescIdDesc(
            board.id!!,
            pageRequest,
        )
    }

    @Test
    fun `list posts filters public posts by title`() {
        val board = board(slug = "notice")
        val pageRequest = PageRequest.of(0, 20)
        val publicPost = post(id = 101L, boardId = board.id!!, title = "수요 예배 안내")
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(
            menuItemRepository.existsByTypeAndStatusAndBoardKey(
                MenuType.BOARD,
                MenuStatus.PUBLISHED,
                "notice",
            )
        ).thenReturn(true)
        whenever(
            postRepository.findPublicPostsByBoardIdAndTitle(
                board.id!!,
                "수요",
                pageRequest,
            )
        ).thenReturn(PageImpl(listOf(publicPost), pageRequest, 1))
        whenever(adminAccountRepository.findAllById(listOf(1L))).thenReturn(listOf(adminAccount()))
        whenever(postAssetRepository.findAllByPostIdIn(listOf(101L))).thenReturn(emptyList())

        val result = service.listPosts(boardSlug = "notice", page = 0, size = 20, title = "  수요 ")

        assertThat(result.posts).hasSize(1)
        assertThat(result.posts.single().title).isEqualTo("수요 예배 안내")
        verify(postRepository).findPublicPostsByBoardIdAndTitle(board.id!!, "수요", pageRequest)
    }

    @Test
    fun `get post throws not found for private post or post under another board`() {
        val board = board(slug = "notice")
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(
            menuItemRepository.existsByTypeAndStatusAndBoardKey(
                MenuType.BOARD,
                MenuStatus.PUBLISHED,
                "notice",
            )
        ).thenReturn(true)
        whenever(postRepository.findByBoardIdAndIdAndIsPublicTrue(board.id!!, 99L)).thenReturn(null)

        assertThrows<NotFoundException> {
            service.getPost(boardSlug = "notice", postId = 99L)
        }

        verify(postAssetRepository, never()).findAllByPostIdOrderBySortOrderAscIdAsc(99L)
    }

    @Test
    fun `get post returns content and attached assets without changing view count`() {
        val board = board(slug = "notice")
        val post = post(
            id = 99L,
            boardId = board.id!!,
            title = "첨부 있는 글",
            contentJson = """{"type":"doc","content":[]}""",
            contentHtml = "<p>첨부 있는 글</p>",
            viewCount = 41L,
        )
        val image = asset(
            id = 201L,
            postId = 99L,
            storedPath = "uploads/2026/notice/image.png",
            sortOrder = 0,
        )
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(
            menuItemRepository.existsByTypeAndStatusAndBoardKey(
                MenuType.BOARD,
                MenuStatus.PUBLISHED,
                "notice",
            )
        ).thenReturn(true)
        whenever(postRepository.findByBoardIdAndIdAndIsPublicTrue(board.id!!, 99L)).thenReturn(post)
        whenever(postAssetRepository.findAllByPostIdOrderBySortOrderAscIdAsc(99L)).thenReturn(listOf(image))
        whenever(adminAccountRepository.findById(1L)).thenReturn(java.util.Optional.of(adminAccount()))
        whenever(uploadProperties.publicBaseUrl).thenReturn("https://cdn.example.com/upload")
        whenever(postRepository.findPreviousPublicPostByBoardId(board.id!!, false, post.createdAt, 99L))
            .thenReturn(post(id = 100L, boardId = board.id!!, title = "이전 글", viewCount = 5))
        whenever(postRepository.findNextPublicPostByBoardId(board.id!!, false, post.createdAt, 99L))
            .thenReturn(post(id = 98L, boardId = board.id!!, title = "다음 글", viewCount = 2))

        val result = service.getPost(boardSlug = "notice", postId = 99L)

        assertThat(result.id).isEqualTo(99L)
        assertThat(result.title).isEqualTo("첨부 있는 글")
        assertThat(result.authorName).isEqualTo("관리자")
        assertThat(result.viewCount).isEqualTo(41L)
        assertThat(post.viewCount).isEqualTo(41L)
        assertThat(result.contentJson).isEqualTo("""{"type":"doc","content":[]}""")
        assertThat(result.contentHtml).isEqualTo("<p>첨부 있는 글</p>")
        assertThat(result.assets).hasSize(1)
        assertThat(result.assets[0].id).isEqualTo(201L)
        assertThat(result.assets[0].kind).isEqualTo(PostAssetKind.INLINE_IMAGE)
        assertThat(result.assets[0].storedPath).isEqualTo("uploads/2026/notice/image.png")
        assertThat(result.assets[0].publicUrl).isEqualTo(
            "https://cdn.example.com/upload/uploads/2026/notice/image.png"
        )
        assertThat(result.previousPost?.id).isEqualTo(100L)
        assertThat(result.previousPost?.title).isEqualTo("이전 글")
        assertThat(result.nextPost?.id).isEqualTo(98L)
        assertThat(result.nextPost?.title).isEqualTo("다음 글")
    }

    @Test
    fun `record post view increments view count through dedicated service call without loading detail graph`() {
        val board = board(slug = "notice")
        val post = post(id = 99L, boardId = board.id!!, title = "조회수 기록 글", viewCount = 7L)
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(
            menuItemRepository.existsByTypeAndStatusAndBoardKey(
                MenuType.BOARD,
                MenuStatus.PUBLISHED,
                "notice",
            )
        ).thenReturn(true)
        whenever(postRepository.findByBoardIdAndIdAndIsPublicTrue(board.id!!, 99L)).thenReturn(post)

        val recordPostView = service.javaClass.methods.singleOrNull {
            it.name == "recordPostView" && it.parameterCount == 3
        }

        assertThat(recordPostView)
            .describedAs("PublicBoardService should expose recordPostView(boardSlug, postId, menuId) for view counting")
            .isNotNull

        recordPostView!!.invoke(service, "notice", 99L, null)

        assertThat(post.viewCount).isEqualTo(8L)
        verify(postAssetRepository, never()).findAllByPostIdOrderBySortOrderAscIdAsc(99L)
        verify(adminAccountRepository, never()).findById(1L)
    }

    @Test
    fun `list posts derives summary media flags from assets and content`() {
        val board = board(slug = "notice")
        val pageRequest = PageRequest.of(0, 20)
        val publicPost = post(
            id = 101L,
            boardId = board.id!!,
            title = "영상과 첨부가 있는 글",
            contentJson = """{"type":"doc","content":[{"type":"youtubeEmbed","attrs":{"videoId":"abc123def45"}}]}""",
            contentHtml = """<p>본문</p><iframe src="https://www.youtube.com/embed/abc123def45"></iframe>""",
        )
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(
            menuItemRepository.existsByTypeAndStatusAndBoardKey(
                MenuType.BOARD,
                MenuStatus.PUBLISHED,
                "notice",
            )
        ).thenReturn(true)
        whenever(
            postRepository.findAllByBoardIdAndIsPublicTrueOrderByIsPinnedDescCreatedAtDescIdDesc(
                board.id!!,
                pageRequest,
            )
        ).thenReturn(PageImpl(listOf(publicPost), pageRequest, 1))
        whenever(adminAccountRepository.findAllById(listOf(1L))).thenReturn(listOf(adminAccount()))
        whenever(postAssetRepository.findAllByPostIdIn(listOf(101L))).thenReturn(
            listOf(
                asset(id = 301L, postId = 101L, storedPath = "uploads/notice/image.png", sortOrder = 0),
                asset(
                    id = 302L,
                    postId = 101L,
                    kind = PostAssetKind.FILE_ATTACHMENT,
                    originalFilename = "notice.pdf",
                    storedPath = "uploads/notice/notice.pdf",
                    sortOrder = 1,
                ),
            )
        )

        val result = service.listPosts(boardSlug = "notice", page = 0, size = 20)

        assertThat(result.posts.single().hasInlineImage).isTrue()
        assertThat(result.posts.single().hasVideoEmbed).isTrue()
        assertThat(result.posts.single().hasAttachments).isTrue()
    }

    @Test
    fun `get post receives menu id and does not return a post from another menu with the same board`() {
        val board = board(slug = "notice")
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(
            menuItemRepository.existsByIdAndTypeAndStatusAndBoardKey(
                1001L,
                MenuType.BOARD,
                MenuStatus.PUBLISHED,
                "notice",
            )
        ).thenReturn(true)
        whenever(postRepository.findByMenuIdAndIdAndIsPublicTrue(1001L, 99L)).thenReturn(null)

        assertThrows<NotFoundException> {
            service.getPost(boardSlug = "notice", menuId = 1001L, postId = 99L)
        }

        verify(postRepository).findByMenuIdAndIdAndIsPublicTrue(1001L, 99L)
        verify(postRepository, never()).findByBoardIdAndIdAndIsPublicTrue(board.id!!, 99L)
        verify(postAssetRepository, never()).findAllByPostIdOrderBySortOrderAscIdAsc(99L)
    }

    @Test
    fun `get post public url composition avoids duplicate slash when upload base url ends with slash`() {
        val board = board(slug = "notice")
        val post = post(id = 99L, boardId = board.id!!, title = "첨부 있는 글")
        val attachment = asset(
            id = 202L,
            postId = 99L,
            kind = PostAssetKind.FILE_ATTACHMENT,
            originalFilename = "bulletin.pdf",
            storedPath = "uploads/2026/notice/bulletin.pdf",
            sortOrder = 0,
        )
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(
            menuItemRepository.existsByTypeAndStatusAndBoardKey(
                MenuType.BOARD,
                MenuStatus.PUBLISHED,
                "notice",
            )
        ).thenReturn(true)
        whenever(postRepository.findByBoardIdAndIdAndIsPublicTrue(board.id!!, 99L)).thenReturn(post)
        whenever(postAssetRepository.findAllByPostIdOrderBySortOrderAscIdAsc(99L)).thenReturn(listOf(attachment))
        whenever(adminAccountRepository.findById(1L)).thenReturn(java.util.Optional.of(adminAccount()))
        whenever(uploadProperties.publicBaseUrl).thenReturn("https://cdn.example.com/upload/")
        whenever(postRepository.findPreviousPublicPostByBoardId(board.id!!, false, post.createdAt, 99L)).thenReturn(null)
        whenever(postRepository.findNextPublicPostByBoardId(board.id!!, false, post.createdAt, 99L)).thenReturn(null)

        val result = service.getPost(boardSlug = "notice", postId = 99L)

        assertThat(result.assets[0].storedPath).isEqualTo("uploads/2026/notice/bulletin.pdf")
        assertThat(result.assets[0].publicUrl).isEqualTo(
            "https://cdn.example.com/upload/uploads/2026/notice/bulletin.pdf"
        )
    }

    private fun board(
        id: Long = 10L,
        slug: String,
    ) = Board(
        id = id,
        slug = slug,
        title = "공지사항",
        type = BoardType.NOTICE,
    )

    private fun post(
        id: Long,
        boardId: Long,
        menuId: Long = 1001L,
        title: String,
        contentJson: String = """{"type":"doc"}""",
        contentHtml: String? = "<p>$title</p>",
        isPublic: Boolean = true,
        viewCount: Long = 0,
    ) = Post(
        id = id,
        boardId = boardId,
        menuId = menuId,
        title = title,
        contentJson = contentJson,
        contentHtml = contentHtml,
        isPublic = isPublic,
        authorId = 1L,
        viewCount = viewCount,
    )

    private fun adminAccount(id: Long = 1L) = AdminAccount(
        id = id,
        username = "admin",
        displayName = "관리자",
        passwordHash = "hash",
        role = AdminAccountRole.ADMIN,
    )

    private fun asset(
        id: Long,
        postId: Long,
        kind: PostAssetKind = PostAssetKind.INLINE_IMAGE,
        originalFilename: String = "image.png",
        storedPath: String,
        sortOrder: Int,
    ) = PostAsset(
        id = id,
        uploadedByActorId = 1L,
        kind = kind,
        originalFilename = originalFilename,
        storedPath = storedPath,
        byteSize = 123L,
        postId = postId,
        mimeType = if (kind == PostAssetKind.INLINE_IMAGE) "image/png" else "application/pdf",
        width = if (kind == PostAssetKind.INLINE_IMAGE) 640 else null,
        height = if (kind == PostAssetKind.INLINE_IMAGE) 480 else null,
        sortOrder = sortOrder,
    )
}
