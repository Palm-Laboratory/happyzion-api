package org.happyzion.api.menu.application

import org.assertj.core.api.Assertions.assertThat
import org.happyzion.api.common.config.PublicMenuCacheConfig
import org.happyzion.api.menu.domain.MenuItem
import org.happyzion.api.menu.domain.MenuStatus
import org.happyzion.api.menu.domain.MenuType
import org.happyzion.api.menu.infrastructure.persistence.MenuItemRepository
import org.happyzion.api.youtube.application.PlaylistDisplayableVideoCountResolver
import org.happyzion.api.youtube.infrastructure.persistence.YouTubePlaylistRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier

class PublicMenuCacheContractTest {

    @Test
    fun `public navigation uses Spring cache proxy`() {
        val menuItemRepository = mock<MenuItemRepository>()
        val context = AnnotationConfigApplicationContext()
        context.register(PublicMenuCacheConfig::class.java)
        context.registerBean(MenuItemRepository::class.java, Supplier { menuItemRepository })
        context.registerBean(YouTubePlaylistRepository::class.java, Supplier { mock<YouTubePlaylistRepository>() })
        context.registerBean(PlaylistDisplayableVideoCountResolver::class.java, Supplier {
            mock<PlaylistDisplayableVideoCountResolver>()
        })
        context.register(PublicMenuService::class.java)
        context.refresh()

        try {
            whenever(menuItemRepository.findAllByStatusOrderBySortOrderAscIdAsc(MenuStatus.PUBLISHED))
                .thenReturn(emptyList())

            val service = context.getBean(PublicMenuService::class.java)
            service.getNavigation()
            service.getNavigation()

            verify(menuItemRepository, times(1))
                .findAllByStatusOrderBySortOrderAscIdAsc(MenuStatus.PUBLISHED)
        } finally {
            context.close()
        }
    }

    @Test
    fun `public path resolution is cached by path`() {
        val menuItemRepository = mock<MenuItemRepository>()
        val context = AnnotationConfigApplicationContext()
        context.register(PublicMenuCacheConfig::class.java)
        context.registerBean(MenuItemRepository::class.java, Supplier { menuItemRepository })
        context.registerBean(YouTubePlaylistRepository::class.java, Supplier { mock<YouTubePlaylistRepository>() })
        context.registerBean(PlaylistDisplayableVideoCountResolver::class.java, Supplier {
            mock<PlaylistDisplayableVideoCountResolver>()
        })
        context.register(PublicMenuService::class.java)
        context.refresh()

        try {
            whenever(menuItemRepository.findAllByStatusOrderBySortOrderAscIdAsc(MenuStatus.PUBLISHED))
                .thenReturn(
                    listOf(
                        MenuItem(
                            id = 1L,
                            type = MenuType.STATIC,
                            label = "예배",
                            slug = "worship",
                            staticPageKey = "worship",
                        ),
                    ),
                )

            val service = context.getBean(PublicMenuService::class.java)
            service.resolveMenuPath("/worship")
            service.resolveMenuPath("/worship")

            verify(menuItemRepository, times(1))
                .findAllByStatusOrderBySortOrderAscIdAsc(MenuStatus.PUBLISHED)
        } finally {
            context.close()
        }
    }

    @Test
    fun `menu mutation paths evict the public menu cache`() {
        val menuManagementService = Files.readString(
            Path.of("src/main/kotlin/org/happyzion/api/menu/application/MenuManagementService.kt"),
        )
        val youTubeSyncService = Files.readString(
            Path.of("src/main/kotlin/org/happyzion/api/youtube/application/YouTubeSyncService.kt"),
        )

        assertThat(menuManagementService).contains(
            "@Transactional\n    @EvictPublicMenuCache\n    fun replaceTree",
            "@Transactional\n    @EvictPublicMenuCache\n    fun deleteMenuItem",
        )
        assertThat(youTubeSyncService).contains(
            "@Transactional\n    @EvictPublicMenuCache\n    fun sync",
        )
    }

    @Test
    fun `cache configuration is caffeine backed and ttl configurable`() {
        val config = Files.readString(
            Path.of("src/main/kotlin/org/happyzion/api/common/config/PublicMenuCacheConfig.kt"),
        )
        val applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"))

        assertThat(config).contains("CaffeineCacheManager", "TransactionAwareCacheManagerProxy", "expireAfterWrite(ttl)")
        assertThat(applicationYaml).contains("PUBLIC_MENU_CACHE_TTL:PT10M")
    }
}
