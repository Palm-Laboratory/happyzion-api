package org.happyzion.api.board.application

import org.happyzion.api.adminaccount.domain.AdminAccount
import org.happyzion.api.adminaccount.domain.AdminAccountRole
import org.happyzion.api.adminaccount.infrastructure.persistence.AdminAccountRepository
import org.happyzion.api.board.domain.Board
import org.happyzion.api.board.domain.BoardType
import org.happyzion.api.board.domain.Post
import org.happyzion.api.board.domain.PostAsset
import org.happyzion.api.board.domain.PostAssetKind
import org.happyzion.api.board.infrastructure.persistence.BoardRepository
import org.happyzion.api.board.infrastructure.persistence.PostAssetRepository
import org.happyzion.api.board.infrastructure.persistence.PostRepository
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.menu.domain.MenuItem
import org.happyzion.api.menu.domain.MenuType
import org.happyzion.api.menu.infrastructure.persistence.MenuItemRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.time.OffsetDateTime
import java.util.Optional

class BoardAdminServiceTest {

    private val boardRepository: BoardRepository = mock()
    private val postRepository: PostRepository = mock()
    private val postAssetRepository: PostAssetRepository = mock()
    private val adminAccountRepository: AdminAccountRepository = mock()
    private val menuItemRepository: MenuItemRepository = mock()

    private val service = BoardAdminService(
        boardRepository = boardRepository,
        postRepository = postRepository,
        postAssetRepository = postAssetRepository,
        adminAccountRepository = adminAccountRepository,
        menuItemRepository = menuItemRepository,
    )

    @BeforeEach
    fun stubMenuItemRepository() {
        whenever(menuItemRepository.findFirstByTypeAndBoardKeyOrderBySortOrderAscIdAsc(any(), any()))
            .thenReturn(stubMenuItem(1001L))
        whenever(menuItemRepository.existsByIdAndTypeAndBoardKey(any(), any(), any()))
            .thenReturn(true)
    }

    @Test
    fun `list boards requires active admin and returns board summaries`() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(Optional.of(activeAdmin(1L)))
        whenever(boardRepository.findAll()).thenReturn(
            listOf(
                Board(
                    id = 10L,
                    slug = "notice",
                    title = "공지사항",
                    type = BoardType.NOTICE,
                    description = "교회 공지",
                ),
                Board(
                    id = 20L,
                    slug = "album",
                    title = "앨범",
                    type = BoardType.ALBUM,
                    description = null,
                ),
            )
        )

        val result = service.listBoards(actorId = 1L)

        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo(10L)
        assertThat(result[0].slug).isEqualTo("notice")
        assertThat(result[0].title).isEqualTo("공지사항")
        assertThat(result[0].type).isEqualTo(BoardType.NOTICE)
        assertThat(result[0].description).isEqualTo("교회 공지")
        assertThat(result[1].slug).isEqualTo("album")
    }

    @Test
    fun `list boards rejects inactive admin`() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(
            Optional.of(activeAdmin(1L).copyForTest(active = false))
        )

        assertThrows<ForbiddenException> {
            service.listBoards(actorId = 1L)
        }

        verify(boardRepository, never()).findAll()
    }

    @Test
    fun `list posts throws not found for unknown board slug`() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(Optional.of(activeAdmin(1L)))
        whenever(boardRepository.findBySlug("missing")).thenReturn(null)

        assertThrows<NotFoundException> {
            service.listPosts(actorId = 1L, boardSlug = "missing")
        }

        verify(postRepository, never()).findAll()
    }

    @Test
    fun `list posts returns paged results with hasNext`() {
        val board = Board(id = 10L, slug = "notice", title = "공지사항", type = BoardType.NOTICE)
        val posts = (1..20).map { i ->
            Post(id = i.toLong(), boardId = 10L, menuId = 1001L, title = "글 $i",
                contentJson = """{"type":"doc"}""", authorId = 1L)
        }
        whenever(adminAccountRepository.findById(1L)).thenReturn(Optional.of(activeAdmin(1L)))
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(postRepository.findAllByBoardIdOrderByIsPinnedDescCreatedAtDescIdDesc(any<Long>(), any<Pageable>()))
            .thenReturn(PageImpl(posts, PageRequest.of(0, 20), 25))

        val result = service.listPosts(actorId = 1L, boardSlug = "notice", page = 0, size = 20)

        assertThat(result.posts).hasSize(20)
        assertThat(result.hasNext).isTrue()
    }

    @Test
    fun `list posts scopes to menu id and returns hasNext false on last page`() {
        val board = Board(id = 10L, slug = "notice", title = "공지사항", type = BoardType.NOTICE)
        val post = Post(id = 99L, boardId = 10L, menuId = 1001L, title = "공지 글",
            contentJson = """{"type":"doc"}""", authorId = 1L)
        whenever(adminAccountRepository.findById(1L)).thenReturn(Optional.of(activeAdmin(1L)))
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(postRepository.findAllByBoardIdAndMenuIdOrderByIsPinnedDescCreatedAtDescIdDesc(any<Long>(), any<Long>(), any<Pageable>()))
            .thenReturn(PageImpl(listOf(post), PageRequest.of(0, 20), 1))

        val result = service.listPosts(actorId = 1L, boardSlug = "notice", menuId = 1001L)

        assertThat(result.posts).hasSize(1)
        assertThat(result.posts[0].id).isEqualTo(99L)
        assertThat(result.hasNext).isFalse()
    }

    @Test
    fun `list posts filters by title keyword`() {
        val board = Board(id = 10L, slug = "notice", title = "공지사항", type = BoardType.NOTICE)
        val post = Post(id = 55L, boardId = 10L, menuId = 1001L, title = "부활절 공지",
            contentJson = """{"type":"doc"}""", authorId = 1L)
        whenever(adminAccountRepository.findById(1L)).thenReturn(Optional.of(activeAdmin(1L)))
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(postRepository.findAdminPostsByBoardIdAndTitle(any<Long>(), any<String>(), any<Pageable>()))
            .thenReturn(PageImpl(listOf(post), PageRequest.of(0, 20), 1))

        val result = service.listPosts(actorId = 1L, boardSlug = "notice", title = "부활절")

        assertThat(result.posts).hasSize(1)
        assertThat(result.posts[0].title).isEqualTo("부활절 공지")
    }

    @Test
    fun `create post requires active admin validates json creates post and attaches provided assets`() {
        val board = Board(id = 10L, slug = "notice", title = "공지사항", type = BoardType.NOTICE)
        val asset1 = uploadedAsset(id = 100L, actorId = 1L)
        val asset2 = uploadedAsset(id = 200L, actorId = 1L)
        whenever(adminAccountRepository.findById(1L)).thenReturn(Optional.of(activeAdmin(1L)))
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(postAssetRepository.findAllById(listOf(100L, 200L))).thenReturn(listOf(asset1, asset2))
        whenever(postRepository.save(any())).thenReturn(
            Post(
                id = 99L,
                boardId = 10L,
                title = "새 소식",
                contentJson = """{"type":"doc","content":[]}""",
                contentHtml = "<p>새 소식</p>",
                isPublic = true,
                authorId = 1L,
            )
        )

        val result = service.createPost(
            actorId = 1L,
            boardSlug = "notice",
            command = BoardPostSaveCommand(
                title = "새 소식",
                contentJson = """{"type":"doc","content":[]}""",
                contentHtml = "<p>새 소식</p>",
                isPublic = true,
                isPinned = true,
                assetIds = listOf(100L, 200L),
            ),
        )

        val savedPost = argumentCaptor<Post>().apply {
            verify(postRepository).save(capture())
        }.firstValue
        assertThat(savedPost.boardId).isEqualTo(10L)
        assertThat(savedPost.title).isEqualTo("새 소식")
        assertThat(savedPost.contentJson).isEqualTo("""{"type":"doc","content":[]}""")
        assertThat(savedPost.contentHtml).isEqualTo("<p>새 소식</p>")
        assertThat(savedPost.isPublic).isTrue()
        assertThat(savedPost.isPinned).isTrue()
        assertThat(savedPost.authorId).isEqualTo(1L)

        val savedAssets = argumentCaptor<Iterable<PostAsset>>().apply {
            verify(postAssetRepository).saveAll(capture())
        }.firstValue.toList()
        assertThat(savedAssets.map { it.id }).containsExactly(100L, 200L)
        assertThat(savedAssets.map { it.postId }).containsExactly(99L, 99L)
        assertThat(savedAssets.map { it.sortOrder }).containsExactly(0, 1)
        assertThat(result.id).isEqualTo(99L)
    }

    @Test
    fun `create post receives menu id and stores it on the saved post`() {
        val board = Board(id = 10L, slug = "notice", title = "공지사항", type = BoardType.NOTICE)
        whenever(adminAccountRepository.findById(1L)).thenReturn(Optional.of(activeAdmin(1L)))
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(postRepository.save(any())).thenReturn(
            Post(
                id = 99L,
                boardId = 10L,
                menuId = 1001L,
                title = "첫 번째 공지 메뉴 글",
                contentJson = """{"type":"doc","content":[]}""",
                contentHtml = "<p>첫 번째 공지 메뉴 글</p>",
                isPublic = true,
                authorId = 1L,
            )
        )

        service.createPost(
            actorId = 1L,
            boardSlug = "notice",
            menuId = 1001L,
            command = BoardPostSaveCommand(
                title = "첫 번째 공지 메뉴 글",
                contentJson = """{"type":"doc","content":[]}""",
                contentHtml = "<p>첫 번째 공지 메뉴 글</p>",
                isPublic = true,
                assetIds = emptyList(),
            ),
        )

        val savedPost = argumentCaptor<Post>().apply {
            verify(postRepository).save(capture())
        }.firstValue
        assertThat(savedPost.boardId).isEqualTo(10L)
        assertThat(savedPost.menuId).isEqualTo(1001L)
    }

    @Test
    fun `create post attaches inline image asset referenced only in content json`() {
        val board = Board(id = 10L, slug = "notice", title = "공지사항", type = BoardType.NOTICE)
        val asset = uploadedAsset(id = 100L, actorId = 1L)
        whenever(adminAccountRepository.findById(1L)).thenReturn(Optional.of(activeAdmin(1L)))
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(postAssetRepository.findById(100L)).thenReturn(Optional.of(asset))
        whenever(postRepository.save(any())).thenReturn(
            Post(
                id = 99L,
                boardId = 10L,
                title = "새 소식",
                contentJson = docWithImage(assetId = 100L),
                contentHtml = "<p>새 소식</p>",
                isPublic = true,
                authorId = 1L,
            )
        )

        service.createPost(
            actorId = 1L,
            boardSlug = "notice",
            command = BoardPostSaveCommand(
                title = "새 소식",
                contentJson = docWithImage(assetId = 100L),
                contentHtml = "<p>새 소식</p>",
                isPublic = true,
                assetIds = emptyList(),
            ),
        )

        val savedAssets = argumentCaptor<Iterable<PostAsset>>().apply {
            verify(postAssetRepository).saveAll(capture())
        }.firstValue.toList()
        assertThat(savedAssets.map { it.id }).containsExactly(100L)
        assertThat(savedAssets.map { it.postId }).containsExactly(99L)
        assertThat(savedAssets.map { it.sortOrder }).containsExactly(0)
    }

    @Test
    fun `create post deduplicates inline image asset when also provided in command asset ids`() {
        val board = Board(id = 10L, slug = "notice", title = "공지사항", type = BoardType.NOTICE)
        val asset = uploadedAsset(id = 100L, actorId = 1L)
        whenever(adminAccountRepository.findById(1L)).thenReturn(Optional.of(activeAdmin(1L)))
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(postAssetRepository.findById(100L)).thenReturn(Optional.of(asset))
        whenever(postAssetRepository.findAllById(listOf(100L))).thenReturn(listOf(asset))
        whenever(postRepository.save(any())).thenReturn(
            Post(
                id = 99L,
                boardId = 10L,
                title = "새 소식",
                contentJson = docWithImage(assetId = 100L),
                contentHtml = "<p>새 소식</p>",
                isPublic = true,
                authorId = 1L,
            )
        )

        service.createPost(
            actorId = 1L,
            boardSlug = "notice",
            command = BoardPostSaveCommand(
                title = "새 소식",
                contentJson = docWithImage(assetId = 100L),
                contentHtml = "<p>새 소식</p>",
                isPublic = true,
                assetIds = listOf(100L),
            ),
        )

        val savedAssets = argumentCaptor<Iterable<PostAsset>>().apply {
            verify(postAssetRepository).saveAll(capture())
        }.firstValue.toList()
        assertThat(savedAssets.map { it.id }).containsExactly(100L)
        assertThat(savedAssets.map { it.postId }).containsExactly(99L)
        assertThat(savedAssets.map { it.sortOrder }).containsExactly(0)
    }

    @Test
    fun `create post rejects invalid content json`() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(Optional.of(activeAdmin(1L)))
        whenever(boardRepository.findBySlug("notice")).thenReturn(
            Board(id = 10L, slug = "notice", title = "공지사항", type = BoardType.NOTICE)
        )

        assertThrows<IllegalArgumentException> {
            service.createPost(
                actorId = 1L,
                boardSlug = "notice",
                command = BoardPostSaveCommand(
                    title = "새 소식",
                    contentJson = "{not-json",
                    contentHtml = "<p>새 소식</p>",
                    isPublic = true,
                    assetIds = emptyList(),
                ),
            )
        }

        verify(postRepository, never()).save(any())
    }

    @Test
    fun `create post rejects public url only image content before saving post`() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(Optional.of(activeAdmin(1L)))
        whenever(boardRepository.findBySlug("notice")).thenReturn(
            Board(id = 10L, slug = "notice", title = "공지사항", type = BoardType.NOTICE)
        )

        assertThrows<IllegalArgumentException> {
            service.createPost(
                actorId = 1L,
                boardSlug = "notice",
                command = BoardPostSaveCommand(
                    title = "새 소식",
                    contentJson = """
                        {
                          "type": "doc",
                          "content": [
                            {
                              "type": "image",
                              "attrs": {
                                "publicUrl": "https://cdn.example.com/uploads/asset-100.png"
                              }
                            }
                          ]
                        }
                    """.trimIndent(),
                    contentHtml = null,
                    isPublic = true,
                    assetIds = emptyList(),
                ),
            )
        }

        verify(postRepository, never()).save(any())
    }

    @Test
    fun `create post rejects asset uploaded by another actor`() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(Optional.of(activeAdmin(1L)))
        whenever(boardRepository.findBySlug("notice")).thenReturn(
            Board(id = 10L, slug = "notice", title = "공지사항", type = BoardType.NOTICE)
        )
        whenever(postAssetRepository.findAllById(listOf(100L))).thenReturn(
            listOf(uploadedAsset(id = 100L, actorId = 2L))
        )

        assertThrows<ForbiddenException> {
            service.createPost(
                actorId = 1L,
                boardSlug = "notice",
                command = BoardPostSaveCommand(
                    title = "새 소식",
                    contentJson = """{"type":"doc"}""",
                    contentHtml = null,
                    isPublic = false,
                    assetIds = listOf(100L),
                ),
            )
        }

        verify(postRepository, never()).save(any())
    }

    @Test
    fun `create post rejects asset already assigned to a different post`() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(Optional.of(activeAdmin(1L)))
        whenever(boardRepository.findBySlug("notice")).thenReturn(
            Board(id = 10L, slug = "notice", title = "공지사항", type = BoardType.NOTICE)
        )
        whenever(postAssetRepository.findAllById(listOf(100L))).thenReturn(
            listOf(uploadedAsset(id = 100L, actorId = 1L, postId = 77L))
        )

        assertThrows<ForbiddenException> {
            service.createPost(
                actorId = 1L,
                boardSlug = "notice",
                command = BoardPostSaveCommand(
                    title = "새 소식",
                    contentJson = """{"type":"doc"}""",
                    contentHtml = null,
                    isPublic = false,
                    assetIds = listOf(100L),
                ),
            )
        }

        verify(postRepository, never()).save(any())
    }

    @Test
    fun `update post rejects posts that are not under the requested board slug`() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(Optional.of(activeAdmin(1L)))
        whenever(boardRepository.findBySlug("notice")).thenReturn(
            Board(id = 10L, slug = "notice", title = "공지사항", type = BoardType.NOTICE)
        )
        whenever(postRepository.findById(99L)).thenReturn(
            Optional.of(
                Post(
                    id = 99L,
                    boardId = 20L,
                    title = "다른 게시판 글",
                    contentJson = """{"type":"doc"}""",
                    authorId = 1L,
                )
            )
        )

        assertThrows<NotFoundException> {
            service.updatePost(
                actorId = 1L,
                boardSlug = "notice",
                postId = 99L,
                command = BoardPostSaveCommand(
                    title = "수정",
                    contentJson = """{"type":"doc"}""",
                    contentHtml = null,
                    isPublic = true,
                    assetIds = emptyList(),
                ),
            )
        }

        verify(postRepository, never()).save(any())
    }

    @Test
    fun `get post rejects posts that are under the same board but another menu`() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(Optional.of(activeAdmin(1L)))
        whenever(boardRepository.findBySlug("notice")).thenReturn(
            Board(id = 10L, slug = "notice", title = "공지사항", type = BoardType.NOTICE)
        )
        whenever(postRepository.findById(99L)).thenReturn(
            Optional.of(
                Post(
                    id = 99L,
                    boardId = 10L,
                    menuId = 2002L,
                    title = "두 번째 공지 메뉴 글",
                    contentJson = """{"type":"doc"}""",
                    authorId = 1L,
                )
            )
        )

        assertThrows<NotFoundException> {
            service.getPost(actorId = 1L, boardSlug = "notice", menuId = 1001L, postId = 99L)
        }

        verify(postAssetRepository, never()).findAllByPostIdOrderBySortOrderAscIdAsc(99L)
    }

    @Test
    fun `update post receives menu id and keeps the post scoped to that menu`() {
        val post = Post(
            id = 99L,
            boardId = 10L,
            menuId = 1001L,
            title = "수정 전",
            contentJson = """{"type":"doc"}""",
            authorId = 1L,
        )
        whenever(adminAccountRepository.findById(1L)).thenReturn(Optional.of(activeAdmin(1L)))
        whenever(boardRepository.findBySlug("notice")).thenReturn(
            Board(id = 10L, slug = "notice", title = "공지사항", type = BoardType.NOTICE)
        )
        whenever(postRepository.findById(99L)).thenReturn(Optional.of(post))
        whenever(postRepository.save(any())).thenAnswer { it.arguments[0] }

        service.updatePost(
            actorId = 1L,
            boardSlug = "notice",
            menuId = 1001L,
            postId = 99L,
            command = BoardPostSaveCommand(
                title = "수정 후",
                contentJson = """{"type":"doc","content":[]}""",
                contentHtml = "<p>수정 후</p>",
                isPublic = false,
                isPinned = true,
                assetIds = emptyList(),
            ),
        )

        val savedPost = argumentCaptor<Post>().apply {
            verify(postRepository).save(capture())
        }.firstValue
        assertThat(savedPost.menuId).isEqualTo(1001L)
        assertThat(savedPost.title).isEqualTo("수정 후")
        assertThat(savedPost.isPinned).isTrue()
    }

    @Test
    fun `update post detaches previously attached assets missing from the save payload`() {
        val board = Board(id = 10L, slug = "notice", title = "공지사항", type = BoardType.NOTICE)
        val post = Post(
            id = 99L,
            boardId = 10L,
            title = "수정 전",
            contentJson = """{"type":"doc"}""",
            authorId = 1L,
        )
        val removedAsset = uploadedAsset(id = 100L, actorId = 1L, postId = 99L)
        val retainedAsset = uploadedAsset(id = 200L, actorId = 1L, postId = 99L).apply {
            detachedAt = OffsetDateTime.parse("2026-04-19T00:00:00Z")
        }
        whenever(adminAccountRepository.findById(1L)).thenReturn(Optional.of(activeAdmin(1L)))
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(postRepository.findById(99L)).thenReturn(Optional.of(post))
        whenever(postRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(postAssetRepository.findAllById(listOf(200L))).thenReturn(listOf(retainedAsset))
        whenever(postAssetRepository.findAllByPostIdOrderBySortOrderAscIdAsc(99L)).thenReturn(listOf(removedAsset, retainedAsset))

        service.updatePost(
            actorId = 1L,
            boardSlug = "notice",
            postId = 99L,
            command = BoardPostSaveCommand(
                title = "수정 후",
                contentJson = """{"type":"doc","content":[]}""",
                contentHtml = "<p>수정 후</p>",
                isPublic = true,
                assetIds = listOf(200L),
            ),
        )

        val savedAssets = argumentCaptor<Iterable<PostAsset>>().apply {
            verify(postAssetRepository).saveAll(capture())
        }.firstValue.toList()
        assertThat(savedAssets.map { it.id }).containsExactly(100L, 200L)
        assertThat(removedAsset.postId).isNull()
        assertThat(removedAsset.detachedAt).isNotNull()
        assertThat(removedAsset.sortOrder).isZero()
        assertThat(retainedAsset.postId).isEqualTo(99L)
        assertThat(retainedAsset.detachedAt).isNull()
        assertThat(retainedAsset.sortOrder).isZero()
    }

    @Test
    fun `get post returns attached assets ordered by sort order and id`() {
        val board = Board(id = 10L, slug = "notice", title = "공지사항", type = BoardType.NOTICE)
        whenever(adminAccountRepository.findById(1L)).thenReturn(Optional.of(activeAdmin(1L)))
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(postRepository.findById(99L)).thenReturn(
            Optional.of(
                Post(
                    id = 99L,
                    boardId = 10L,
                    title = "첨부 있는 글",
                    contentJson = """{"type":"doc"}""",
                    contentHtml = "<p>첨부 있는 글</p>",
                    isPublic = true,
                    authorId = 1L,
                )
            )
        )
        val image = uploadedAsset(id = 100L, actorId = 1L, postId = 99L).apply {
            sortOrder = 0
        }
        val download = uploadedAsset(id = 101L, actorId = 1L, postId = 99L).apply {
            kind = PostAssetKind.FILE_ATTACHMENT
            originalFilename = "bulletin.pdf"
            storedPath = "uploads/bulletin.pdf"
            mimeType = "application/pdf"
            byteSize = 456L
            width = null
            height = null
            sortOrder = 1
        }
        whenever(postAssetRepository.findAllByPostIdOrderBySortOrderAscIdAsc(99L)).thenReturn(
            listOf(image, download)
        )

        val result = service.getPost(actorId = 1L, boardSlug = "notice", postId = 99L)

        assertThat(result.assets).hasSize(2)
        assertThat(result.assets.map { it.id }).containsExactly(100L, 101L)
        assertThat(result.assets.map { it.kind }).containsExactly(PostAssetKind.INLINE_IMAGE, PostAssetKind.FILE_ATTACHMENT)
        assertThat(result.assets.map { it.originalFilename }).containsExactly("asset-100.png", "bulletin.pdf")
        assertThat(result.assets.map { it.storedPath }).containsExactly("uploads/asset-100.png", "uploads/bulletin.pdf")
        assertThat(result.assets.map { it.mimeType }).containsExactly("image/png", "application/pdf")
        assertThat(result.assets.map { it.byteSize }).containsExactly(123L, 456L)
        assertThat(result.assets.map { it.width }).containsExactly(640, null)
        assertThat(result.assets.map { it.height }).containsExactly(480, null)
        assertThat(result.assets.map { it.sortOrder }).containsExactly(0, 1)
        verify(postAssetRepository).findAllByPostIdOrderBySortOrderAscIdAsc(99L)
    }

    @Test
    fun `delete post deletes the post after active admin and board ownership checks`() {
        val board = Board(id = 10L, slug = "notice", title = "공지사항", type = BoardType.NOTICE)
        val post = Post(
            id = 99L,
            boardId = 10L,
            title = "삭제할 글",
            contentJson = """{"type":"doc"}""",
            authorId = 1L,
        )
        whenever(adminAccountRepository.findById(1L)).thenReturn(Optional.of(activeAdmin(1L)))
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(postRepository.findById(99L)).thenReturn(Optional.of(post))
        whenever(postAssetRepository.findAllByPostIdOrderBySortOrderAscIdAsc(99L)).thenReturn(emptyList())

        service.deletePost(actorId = 1L, boardSlug = "notice", postId = 99L)

        verify(postRepository).delete(post)
        verify(postAssetRepository, never()).saveAll(any<Iterable<PostAsset>>())
    }

    @Test
    fun `delete post detaches attached assets before deleting the post so files are cleaned up by gc`() {
        val board = Board(id = 10L, slug = "notice", title = "공지사항", type = BoardType.NOTICE)
        val post = Post(
            id = 99L,
            boardId = 10L,
            title = "삭제할 글",
            contentJson = """{"type":"doc"}""",
            authorId = 1L,
        )
        val asset1 = uploadedAsset(id = 100L, actorId = 1L, postId = 99L)
        val asset2 = uploadedAsset(id = 200L, actorId = 1L, postId = 99L)
        whenever(adminAccountRepository.findById(1L)).thenReturn(Optional.of(activeAdmin(1L)))
        whenever(boardRepository.findBySlug("notice")).thenReturn(board)
        whenever(postRepository.findById(99L)).thenReturn(Optional.of(post))
        whenever(postAssetRepository.findAllByPostIdOrderBySortOrderAscIdAsc(99L)).thenReturn(listOf(asset1, asset2))

        service.deletePost(actorId = 1L, boardSlug = "notice", postId = 99L)

        assertThat(asset1.postId).isNull()
        assertThat(asset1.detachedAt).isNotNull()
        assertThat(asset2.postId).isNull()
        assertThat(asset2.detachedAt).isNotNull()
        val savedAssets = argumentCaptor<Iterable<PostAsset>>().apply {
            verify(postAssetRepository).saveAll(capture())
        }.firstValue.toList()
        assertThat(savedAssets.map { it.id }).containsExactlyInAnyOrder(100L, 200L)
        verify(postRepository).delete(post)
    }

    private fun activeAdmin(id: Long) = AdminAccount(
        id = id,
        username = "admin$id",
        displayName = "관리자$id",
        passwordHash = "hash",
        role = AdminAccountRole.ADMIN,
        active = true,
    )

    private fun AdminAccount.copyForTest(active: Boolean) = AdminAccount(
        id = id,
        username = username,
        displayName = displayName,
        passwordHash = passwordHash,
        role = role,
        active = active,
        lastLoginAt = lastLoginAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun uploadedAsset(
        id: Long,
        actorId: Long,
        postId: Long? = null,
    ) = PostAsset(
        id = id,
        uploadedByActorId = actorId,
        kind = PostAssetKind.INLINE_IMAGE,
        originalFilename = "asset-$id.png",
        storedPath = "uploads/asset-$id.png",
        byteSize = 123L,
        postId = postId,
        mimeType = "image/png",
        width = 640,
        height = 480,
    )

    private fun stubMenuItem(menuId: Long, boardKey: String = "notice") = MenuItem(
        id = menuId,
        type = MenuType.BOARD,
        boardKey = boardKey,
        label = boardKey,
        slug = boardKey,
    )

    private fun docWithImage(assetId: Long) = """
        {
          "type": "doc",
          "content": [
            {
              "type": "image",
              "attrs": {
                "assetId": $assetId,
                "storedPath": "uploads/asset-$assetId.png"
              }
            }
          ]
        }
    """.trimIndent()
}
