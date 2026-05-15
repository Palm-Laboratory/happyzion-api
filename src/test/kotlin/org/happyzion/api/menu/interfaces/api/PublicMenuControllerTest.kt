package org.happyzion.api.menu.interfaces.api

import org.assertj.core.api.Assertions.assertThat
import org.happyzion.api.menu.application.PublicNavigationResponse
import org.happyzion.api.menu.application.PublicResolvedMenuPage
import org.happyzion.api.menu.application.PublicVideoDetail
import org.happyzion.api.menu.application.PublicMenuService
import org.happyzion.api.menu.domain.MenuType
import org.happyzion.api.youtube.domain.YouTubeContentForm
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders

class PublicMenuControllerTest {

    private val publicMenuService: PublicMenuService = mock()

    @Test
    fun `get menu returns public cache control header`() {
        val controller = controller()
        whenever(publicMenuService.getNavigation()).thenReturn(PublicNavigationResponse(groups = emptyList()))

        val response = controller.getMenu()

        assertThat(response.headers.getFirst(HttpHeaders.CACHE_CONTROL))
            .isEqualTo("public, max-age=60, stale-while-revalidate=300")
        assertThat(response.body?.groups).isEmpty()
        verify(publicMenuService).getNavigation()
    }

    @Test
    fun `resolve menu path returns public cache control header`() {
        val controller = controller()
        whenever(publicMenuService.resolveMenuPath("/about")).thenReturn(
            PublicResolvedMenuPage(
                menuId = 1L,
                type = MenuType.STATIC,
                label = "교회소개",
                slug = "about",
                fullPath = "/about",
                parentLabel = null,
                staticPageKey = "about",
                boardKey = null,
                redirectTo = null,
            ),
        )

        val response = controller.resolveMenuPath("/about")

        assertThat(response.headers.getFirst(HttpHeaders.CACHE_CONTROL))
            .isEqualTo("public, max-age=60, stale-while-revalidate=300")
        assertThat(response.body?.menuId).isEqualTo(1L)
        assertThat(response.body?.fullPath).isEqualTo("/about")
        verify(publicMenuService).resolveMenuPath("/about")
    }

    @Test
    fun `video detail endpoint remains a plain response without menu cache headers`() {
        val controller = controller()
        whenever(publicMenuService.getVideoDetailByPath("/videos/worship")).thenReturn(
            PublicVideoDetail(
                title = "주일예배",
                sourceTitle = "주일예배",
                playlistId = "playlist-1",
                slug = "worship",
                fullPath = "/videos/worship",
                description = null,
                thumbnailUrl = null,
                itemCount = 3,
                contentForm = YouTubeContentForm.LONGFORM,
                groupLabel = null,
                siblings = emptyList(),
            ),
        )

        val response = controller.getVideoDetailByPath("/videos/worship")

        assertThat(response.title).isEqualTo("주일예배")
        verify(publicMenuService).getVideoDetailByPath("/videos/worship")
    }

    private fun controller(): PublicMenuController =
        PublicMenuController(publicMenuService = publicMenuService)
}
