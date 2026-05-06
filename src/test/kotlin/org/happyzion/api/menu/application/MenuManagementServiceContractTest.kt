package org.happyzion.api.menu.application

import com.fasterxml.jackson.databind.ObjectMapper
import org.happyzion.api.adminaccount.domain.AdminAccount
import org.happyzion.api.adminaccount.domain.AdminAccountRole
import org.happyzion.api.adminaccount.infrastructure.persistence.AdminAccountRepository
import org.happyzion.api.board.domain.Board
import org.happyzion.api.board.domain.BoardType
import org.happyzion.api.board.infrastructure.persistence.BoardRepository
import org.happyzion.api.board.infrastructure.persistence.PostRepository
import org.happyzion.api.menu.domain.MenuItem
import org.happyzion.api.menu.domain.MenuStatus
import org.happyzion.api.menu.domain.MenuType
import org.happyzion.api.menu.infrastructure.persistence.MenuItemRepository
import org.happyzion.api.menu.infrastructure.persistence.MenuRevisionRepository
import org.happyzion.api.youtube.application.PlaylistDisplayableVideoCountResolver
import org.happyzion.api.youtube.infrastructure.persistence.YouTubePlaylistRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional

class MenuManagementServiceContractTest {

    @Test
    fun `BOARD menu creation should not require admins to choose a board type`() {
        val service = Path.of("src/main/kotlin/org/happyzion/api/menu/application/MenuManagementService.kt")

        assertThat(service).exists()

        val normalized = Files.readString(service).lowercase()

        assertThat(normalized).doesNotContain("게시판 메뉴는 게시판 타입이 필요합니다")
        assertThat(normalized).contains("ensuremenuscopedboard(saved, node.boardtype)")
        assertThat(normalized).doesNotContain("boardtyperepository")
    }

    @Test
    fun `deleting a menu removes menu scoped boards in the deleted subtree first`() {
        val menuItemRepository = mock<MenuItemRepository>()
        val menuRevisionRepository = mock<MenuRevisionRepository>()
        val adminAccountRepository = mock<AdminAccountRepository>()
        val boardRepository = mock<BoardRepository>()

        val postRepository = mock<PostRepository>()
        val youTubePlaylistRepository = mock<YouTubePlaylistRepository>()
        val playlistDisplayableVideoCountResolver = mock<PlaylistDisplayableVideoCountResolver>()
        val service = MenuManagementService(
            menuItemRepository = menuItemRepository,
            menuRevisionRepository = menuRevisionRepository,
            adminAccountRepository = adminAccountRepository,
            boardRepository = boardRepository,

            postRepository = postRepository,
            youTubePlaylistRepository = youTubePlaylistRepository,
            playlistDisplayableVideoCountResolver = playlistDisplayableVideoCountResolver,
            objectMapper = ObjectMapper(),
        )
        val root = MenuItem(id = 10L, type = MenuType.FOLDER, label = "소식", slug = "news")
        val boardMenu = MenuItem(
            id = 11L,
            parentId = 10L,
            type = MenuType.BOARD,
            label = "행사",
            slug = "event",
            boardKey = "news-event",
        )
        val board = Board(
            id = 99L,
            slug = "news-event",
            title = "행사",
            type = BoardType.GENERAL,
            menuId = 11L,
        )

        whenever(adminAccountRepository.findById(1L)).thenReturn(Optional.of(activeAdmin()))
        whenever(menuItemRepository.findById(10L)).thenReturn(Optional.of(root))
        whenever(menuItemRepository.findAllByOrderBySortOrderAscIdAsc())
            .thenReturn(listOf(root, boardMenu), emptyList())
        whenever(boardRepository.findAllByMenuIdIn(setOf(10L, 11L))).thenReturn(listOf(board))

        service.deleteMenuItem(actorId = 1L, menuId = 10L)

        val order = inOrder(boardRepository, menuItemRepository)
        order.verify(boardRepository).deleteAll(listOf(board))
        order.verify(menuItemRepository).delete(root)
    }

    @Test
    fun `manual menu tree saves cannot set DRAFT status`() {
        val menuItemRepository = mock<MenuItemRepository>()
        val service = MenuManagementService(
            menuItemRepository = menuItemRepository,
            menuRevisionRepository = mock<MenuRevisionRepository>(),
            adminAccountRepository = mock<AdminAccountRepository>().also {
                whenever(it.findById(1L)).thenReturn(Optional.of(activeAdmin()))
            },
            boardRepository = mock<BoardRepository>(),

            postRepository = mock<PostRepository>(),
            youTubePlaylistRepository = mock<YouTubePlaylistRepository>(),
            playlistDisplayableVideoCountResolver = mock<PlaylistDisplayableVideoCountResolver>(),
            objectMapper = ObjectMapper(),
        )

        whenever(menuItemRepository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(emptyList())

        assertThatThrownBy {
            service.replaceTree(
                actorId = 1L,
                items = listOf(
                    MenuTreeNodeInput(
                        type = MenuType.FOLDER,
                        status = MenuStatus.DRAFT,
                        label = "새 메뉴",
                        slug = "new-menu",
                        isAuto = true,
                    )
                ),
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("DRAFT 상태는 자동 유튜브 메뉴 최초 동기화에만 사용할 수 있습니다.")
    }

    @Test
    fun `V1 migration should not carry orphan board cleanup migrations into the fresh schema`() {
        val migration = Path.of("src/main/resources/db/migration/V1__create_happyzion_schema.sql")

        assertThat(migration).exists()

        val normalized = Files.readString(migration).lowercase()

        assertThat(normalized).contains("create table board")
        assertThat(normalized).contains("menu_id bigint references menu_item(id) on delete set null")
        assertThat(normalized).contains("create unique index uq_board_menu_id")
        assertThat(normalized).doesNotContain("delete from board")
        assertThat(normalized).doesNotContain("legacy-board-posts")
    }

    private fun activeAdmin() = AdminAccount(
        id = 1L,
        username = "admin",
        displayName = "관리자",
        passwordHash = "hash",
        role = AdminAccountRole.ADMIN,
        active = true,
    )
}
