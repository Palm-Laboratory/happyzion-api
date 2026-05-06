package org.happyzion.api.menu.application

import org.happyzion.api.menu.domain.MenuItem
import org.happyzion.api.menu.domain.MenuStatus
import org.happyzion.api.menu.domain.MenuType
import org.happyzion.api.menu.infrastructure.persistence.MenuItemRepository
import org.happyzion.api.youtube.application.PlaylistDisplayableVideoCountResolver
import org.happyzion.api.youtube.domain.YouTubeContentForm
import org.happyzion.api.youtube.domain.YouTubePlaylist
import org.happyzion.api.youtube.infrastructure.persistence.YouTubePlaylistRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PublicMenuServiceTest {
    private val menuItemRepository = mock<MenuItemRepository>()
    private val youTubePlaylistRepository = mock<YouTubePlaylistRepository>()
    private val playlistDisplayableVideoCountResolver = mock<PlaylistDisplayableVideoCountResolver>()

    private val service = PublicMenuService(
        menuItemRepository = menuItemRepository,
        youTubePlaylistRepository = youTubePlaylistRepository,
        playlistDisplayableVideoCountResolver = playlistDisplayableVideoCountResolver,
    )

    @Test
    fun `resolveMenuPath resolves published child menu without extra slug lookups`() {
        val about = menuItem(
            id = 10L,
            type = MenuType.FOLDER,
            label = "교회소개",
            slug = "about",
        )
        val greeting = menuItem(
            id = 11L,
            parentId = about.id,
            type = MenuType.STATIC,
            label = "인사말",
            slug = "greeting",
            staticPageKey = "about.greeting",
        )
        whenever(menuItemRepository.findAllByStatusOrderBySortOrderAscIdAsc(MenuStatus.PUBLISHED))
            .thenReturn(listOf(about, greeting))

        val resolved = service.resolveMenuPath("/about/greeting")

        assertAll(
            { assertEquals(MenuType.STATIC, resolved.type) },
            { assertEquals("인사말", resolved.label) },
            { assertEquals("greeting", resolved.slug) },
            { assertEquals("/about/greeting", resolved.fullPath) },
            { assertEquals("교회소개", resolved.parentLabel) },
            { assertEquals("about.greeting", resolved.staticPageKey) },
            { assertNull(resolved.redirectTo) },
        )

        verify(menuItemRepository).findAllByStatusOrderBySortOrderAscIdAsc(MenuStatus.PUBLISHED)
        verify(menuItemRepository, never()).findRootBySlug("about")
        verify(menuItemRepository, never()).findByParentIdAndSlug(about.id!!, "greeting")
    }

    @Test
    fun `resolveMenuPath redirects folder to first published child without extra slug lookups`() {
        val about = menuItem(
            id = 20L,
            type = MenuType.FOLDER,
            label = "교회소개",
            slug = "about",
        )
        val greeting = menuItem(
            id = 21L,
            parentId = about.id,
            type = MenuType.STATIC,
            label = "인사말",
            slug = "greeting",
            staticPageKey = "about.greeting",
            sortOrder = 0,
        )
        val history = menuItem(
            id = 22L,
            parentId = about.id,
            type = MenuType.STATIC,
            label = "연혁",
            slug = "history",
            staticPageKey = "about.history",
            sortOrder = 1,
        )
        whenever(menuItemRepository.findAllByStatusOrderBySortOrderAscIdAsc(MenuStatus.PUBLISHED))
            .thenReturn(listOf(about, greeting, history))

        val resolved = service.resolveMenuPath("/about")

        assertAll(
            { assertEquals(MenuType.FOLDER, resolved.type) },
            { assertEquals("교회소개", resolved.label) },
            { assertEquals("about", resolved.slug) },
            { assertEquals("/about/greeting", resolved.fullPath) },
            { assertEquals("/about/greeting", resolved.redirectTo) },
            { assertNull(resolved.parentLabel) },
        )

        verify(menuItemRepository).findAllByStatusOrderBySortOrderAscIdAsc(MenuStatus.PUBLISHED)
        verify(menuItemRepository, never()).findRootBySlug("about")
        verify(menuItemRepository, never()).findByParentIdAndSlug(about.id!!, "greeting")
    }

    @Test
    fun `resolveMenuPath resolves published board child using menu tree path instead of board key hash`() {
        val news = menuItem(
            id = 30L,
            type = MenuType.FOLDER,
            label = "소식",
            slug = "news",
        )
        val notice = menuItem(
            id = 31L,
            parentId = news.id,
            type = MenuType.BOARD,
            label = "공지사항",
            slug = "notice",
            boardKey = "notice",
        )
        whenever(menuItemRepository.findAllByStatusOrderBySortOrderAscIdAsc(MenuStatus.PUBLISHED))
            .thenReturn(listOf(news, notice))

        val resolved = service.resolveMenuPath("/news/notice")

        assertAll(
            { assertEquals(MenuType.BOARD, resolved.type) },
            { assertEquals("공지사항", resolved.label) },
            { assertEquals("notice", resolved.slug) },
            { assertEquals("/news/notice", resolved.fullPath) },
            { assertEquals("소식", resolved.parentLabel) },
            { assertEquals("notice", resolved.boardKey) },
            { assertNull(resolved.redirectTo) },
        )

        verify(menuItemRepository).findAllByStatusOrderBySortOrderAscIdAsc(MenuStatus.PUBLISHED)
        verify(menuItemRepository, never()).findRootBySlug("news")
        verify(menuItemRepository, never()).findByParentIdAndSlug(news.id!!, "notice")
    }

    @Test
    fun `resolveMenuPath redirects folder to first published board child using menu tree path`() {
        val news = menuItem(
            id = 40L,
            type = MenuType.FOLDER,
            label = "소식",
            slug = "news",
        )
        val notice = menuItem(
            id = 41L,
            parentId = news.id,
            type = MenuType.BOARD,
            label = "공지사항",
            slug = "notice",
            boardKey = "notice",
            sortOrder = 0,
        )
        val bulletin = menuItem(
            id = 42L,
            parentId = news.id,
            type = MenuType.BOARD,
            label = "주보",
            slug = "bulletin",
            boardKey = "bulletin",
            sortOrder = 1,
        )
        whenever(menuItemRepository.findAllByStatusOrderBySortOrderAscIdAsc(MenuStatus.PUBLISHED))
            .thenReturn(listOf(news, notice, bulletin))

        val resolved = service.resolveMenuPath("/news")

        assertAll(
            { assertEquals(MenuType.FOLDER, resolved.type) },
            { assertEquals("소식", resolved.label) },
            { assertEquals("news", resolved.slug) },
            { assertEquals("/news/notice", resolved.fullPath) },
            { assertEquals("/news/notice", resolved.redirectTo) },
            { assertNull(resolved.parentLabel) },
        )

        verify(menuItemRepository).findAllByStatusOrderBySortOrderAscIdAsc(MenuStatus.PUBLISHED)
        verify(menuItemRepository, never()).findRootBySlug("news")
        verify(menuItemRepository, never()).findByParentIdAndSlug(news.id!!, "notice")
    }

    @Test
    fun `getVideoDetailByPath resolves playlist detail from published items without parent lookup`() {
        val worship = menuItem(
            id = 51L,
            type = MenuType.YOUTUBE_PLAYLIST_GROUP,
            label = "예배",
            slug = "worship",
        )
        val sunday = menuItem(
            id = 52L,
            parentId = worship.id,
            type = MenuType.YOUTUBE_PLAYLIST,
            label = "주일예배",
            slug = "sunday",
            playlistId = 101L,
            playlistContentForm = YouTubeContentForm.LONGFORM,
            sortOrder = 0,
        )
        val friday = menuItem(
            id = 53L,
            parentId = worship.id,
            type = MenuType.YOUTUBE_PLAYLIST,
            label = "금요예배",
            slug = "friday",
            playlistId = 102L,
            playlistContentForm = YouTubeContentForm.LONGFORM,
            sortOrder = 1,
        )
        whenever(menuItemRepository.findAllByStatusOrderBySortOrderAscIdAsc(MenuStatus.PUBLISHED))
            .thenReturn(listOf(worship, sunday, friday))
        whenever(youTubePlaylistRepository.findById(eq(101L))).thenReturn(
            java.util.Optional.of(
                YouTubePlaylist(
                    id = 101L,
                    channelId = 1L,
                    playlistId = "PL_SUNDAY",
                    title = "주일예배 원본",
                    description = "예배 모음",
                    thumbnailUrl = "https://example.com/thumb.jpg",
                    itemCount = 42,
                    publishedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                ),
            ),
        )

        val detail = service.getVideoDetailByPath("/worship/sunday")

        assertAll(
            { assertEquals("주일예배", detail.title) },
            { assertEquals("주일예배 원본", detail.sourceTitle) },
            { assertEquals("PL_SUNDAY", detail.playlistId) },
            { assertEquals("/worship/sunday", detail.fullPath) },
            { assertEquals("예배", detail.groupLabel) },
            { assertEquals(2, detail.siblings.size) },
            {
                assertEquals(
                    listOf("/worship/sunday", "/worship/friday"),
                    detail.siblings.map { it.href },
                )
            },
        )

        verify(menuItemRepository).findAllByStatusOrderBySortOrderAscIdAsc(MenuStatus.PUBLISHED)
        verify(menuItemRepository, never()).findById(worship.id!!)
    }

    @Test
    fun `video menu paths follow the menu tree without a fixed videos prefix`() {
        val videos = menuItem(
            id = 60L,
            type = MenuType.YOUTUBE_PLAYLIST_GROUP,
            label = "영상",
            slug = "videos",
        )
        val worship = menuItem(
            id = 61L,
            parentId = videos.id,
            type = MenuType.YOUTUBE_PLAYLIST,
            label = "예배",
            slug = "worship",
            playlistId = 201L,
            playlistContentForm = YouTubeContentForm.LONGFORM,
        )
        whenever(menuItemRepository.findAllByStatusOrderBySortOrderAscIdAsc(MenuStatus.PUBLISHED))
            .thenReturn(listOf(videos, worship))
        whenever(youTubePlaylistRepository.findById(eq(201L))).thenReturn(
            java.util.Optional.of(
                YouTubePlaylist(
                    id = 201L,
                    channelId = 1L,
                    playlistId = "PL_WORSHIP",
                    title = "예배 원본",
                    description = "예배 영상",
                    thumbnailUrl = null,
                    itemCount = 10,
                    publishedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                ),
            ),
        )

        val navigation = service.getNavigation()
        val detail = service.getVideoDetailByPath("/videos/worship")

        assertAll(
            { assertEquals("/videos/worship", navigation.groups.single().href) },
            { assertEquals("/videos/worship", navigation.groups.single().defaultLandingHref) },
            { assertEquals("/videos/worship", navigation.groups.single().items.single().href) },
            { assertEquals("/videos/worship", detail.fullPath) },
        )
    }

    private fun menuItem(
        id: Long,
        parentId: Long? = null,
        type: MenuType,
        label: String,
        slug: String,
        staticPageKey: String? = null,
        boardKey: String? = null,
        playlistId: Long? = null,
        playlistContentForm: YouTubeContentForm? = null,
        sortOrder: Int = 0,
    ): MenuItem = MenuItem(
        id = id,
        parentId = parentId,
        type = type,
        status = MenuStatus.PUBLISHED,
        label = label,
        slug = slug,
        staticPageKey = staticPageKey,
        boardKey = boardKey,
        playlistId = playlistId,
        playlistContentForm = playlistContentForm,
        sortOrder = sortOrder,
        depth = if (parentId == null) 0 else 1,
        path = slug,
    )
}
