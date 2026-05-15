package org.happyzion.api.menu.application

import org.happyzion.api.common.config.PublicMenuCacheNames
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.menu.domain.MenuItem
import org.happyzion.api.menu.domain.MenuStatus
import org.happyzion.api.menu.domain.MenuType
import org.happyzion.api.menu.infrastructure.persistence.MenuItemRepository
import org.happyzion.api.youtube.application.PlaylistDisplayableVideoCountResolver
import org.happyzion.api.youtube.domain.YouTubeContentForm
import org.happyzion.api.youtube.infrastructure.persistence.YouTubePlaylistRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PublicMenuService(
    private val menuItemRepository: MenuItemRepository,
    private val youTubePlaylistRepository: YouTubePlaylistRepository,
    private val playlistDisplayableVideoCountResolver: PlaylistDisplayableVideoCountResolver,
) {
    private val menuOrder = compareBy<MenuItem> { it.sortOrder }.thenBy { it.id }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = [PublicMenuCacheNames.NAVIGATION])
    fun getNavigation(): PublicNavigationResponse {
        val publishedItems = menuItemRepository.findAllByStatusOrderBySortOrderAscIdAsc(MenuStatus.PUBLISHED)
        val childrenByParent = publishedItems.groupBy { it.parentId }
        val itemsById = publishedItems.associateBy { it.id!! }
        val rootItems = childrenByParent[null].orEmpty()
            .sortedWith(menuOrder)

        val groups = rootItems.map { root ->
            val directChildren = childrenByParent[root.id].orEmpty()
                .sortedWith(menuOrder)
            val groupHref = resolveHref(root, childrenByParent, itemsById)
            val defaultLandingHref = resolveDefaultLandingHref(root, childrenByParent, itemsById)
            NavigationGroupDto(
                key = root.slug,
                type = root.type,
                label = root.label,
                href = groupHref,
                matchPath = normalizeMatchPath(groupHref),
                linkType = resolveLinkType(root),
                openInNewTab = root.openInNewTab,
                visible = true,
                headerVisible = true,
                mobileVisible = true,
                lnbVisible = true,
                breadcrumbVisible = true,
                defaultLandingHref = defaultLandingHref,
                items = directChildren.map { child ->
                    val href = resolveHref(child, childrenByParent, itemsById)
                    NavigationItemDto(
                        key = child.slug,
                        type = child.type,
                        label = child.label,
                        href = href,
                        matchPath = normalizeMatchPath(href),
                        linkType = resolveLinkType(child),
                        contentSiteKey = child.staticPageKey ?: child.boardKey,
                        openInNewTab = child.openInNewTab,
                        visible = true,
                        headerVisible = true,
                        mobileVisible = true,
                        lnbVisible = true,
                        breadcrumbVisible = true,
                        defaultLanding = false,
                    )
                },
            )
        }

        return PublicNavigationResponse(groups = groups)
    }

    @Transactional(readOnly = true)
    fun getVideoDetailByPath(path: String): PublicVideoDetail {
        val publishedItems = menuItemRepository.findAllByStatusOrderBySortOrderAscIdAsc(MenuStatus.PUBLISHED)
        val childrenByParent = publishedItems.groupBy { it.parentId }
        val itemsById = publishedItems.associateBy { it.id!! }
        val menu = resolvePublishedVideoMenu(path, childrenByParent)
        return buildVideoDetail(menu, publishedItems, itemsById)
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = [PublicMenuCacheNames.RESOLVE], key = "#path")
    fun resolveMenuPath(path: String): PublicResolvedMenuPage {
        val publishedItems = menuItemRepository.findAllByStatusOrderBySortOrderAscIdAsc(MenuStatus.PUBLISHED)
        val childrenByParent = publishedItems.groupBy { it.parentId }
        val itemsById = publishedItems.associateBy { it.id!! }
        val normalizedPath = normalizeLookupPath(path)
        val menu = resolvePublishedMenu(normalizedPath, childrenByParent)

        if (menu.type == MenuType.FOLDER || menu.type == MenuType.YOUTUBE_PLAYLIST_GROUP) {
            val redirectTo = resolveDefaultLandingHref(menu, childrenByParent, itemsById)
                ?: throw NotFoundException("연결된 공개 페이지를 찾을 수 없습니다. path=$path")

            return PublicResolvedMenuPage(
                menuId = menu.id ?: throw IllegalStateException("메뉴 id가 없습니다."),
                type = menu.type,
                label = menu.label,
                slug = menu.slug,
                fullPath = redirectTo,
                parentLabel = null,
                staticPageKey = null,
                boardKey = null,
                redirectTo = redirectTo,
            )
        }

        return PublicResolvedMenuPage(
            menuId = menu.id ?: throw IllegalStateException("메뉴 id가 없습니다."),
            type = menu.type,
            label = menu.label,
            slug = menu.slug,
            fullPath = resolveHref(menu, childrenByParent, itemsById),
            parentLabel = menu.parentId?.let(itemsById::get)?.label,
            staticPageKey = menu.staticPageKey,
            boardKey = menu.boardKey,
            redirectTo = null,
        )
    }

    private fun buildVideoDetail(
        menu: MenuItem,
        publishedItems: List<MenuItem>,
        itemsById: Map<Long, MenuItem>,
    ): PublicVideoDetail {
        if (menu.type != MenuType.YOUTUBE_PLAYLIST || menu.status != MenuStatus.PUBLISHED) {
            throw NotFoundException("공개된 재생목록을 찾을 수 없습니다. menuId=${menu.id}")
        }

        val playlist = menu.playlistId?.let { youTubePlaylistRepository.findByIdOrNull(it) }
            ?: throw NotFoundException("유튜브 재생목록 정보를 찾을 수 없습니다. menuId=${menu.id}")

        val siblings = menu.parentId?.let { parentId ->
            publishedItems
                .filter { it.parentId == parentId && it.type == MenuType.YOUTUBE_PLAYLIST }
                .sortedWith(menuOrder)
                .map {
                    VideoSiblingLink(
                        label = it.label,
                        href = buildStableHref(it, itemsById),
                    )
                }
        }.orEmpty()

        val groupLabel = menu.parentId?.let { parentId ->
            itemsById[parentId]?.label
        }

        return PublicVideoDetail(
            title = menu.label,
            sourceTitle = playlist.title,
            playlistId = playlist.playlistId,
            slug = menu.slug,
            fullPath = buildStableHref(menu, itemsById),
            description = playlist.description,
            thumbnailUrl = playlist.thumbnailUrl,
            itemCount = playlistDisplayableVideoCountResolver.resolve(playlist.id!!),
            contentForm = menu.playlistContentForm ?: YouTubeContentForm.LONGFORM,
            groupLabel = groupLabel,
            siblings = siblings,
        )
    }

    private fun resolveHref(
        item: MenuItem,
        childrenByParent: Map<Long?, List<MenuItem>>,
        itemsById: Map<Long, MenuItem>,
    ): String =
        when (item.type) {
            MenuType.STATIC -> buildMenuPath(item, itemsById)
            MenuType.BOARD -> buildMenuPath(item, itemsById)
            MenuType.YOUTUBE_PLAYLIST -> buildStableHref(item, itemsById)
            MenuType.EXTERNAL_LINK -> item.externalUrl ?: "/"
            MenuType.FOLDER,
            MenuType.YOUTUBE_PLAYLIST_GROUP -> resolveDefaultLandingHref(item, childrenByParent, itemsById) ?: "/"
        }

    private fun resolveDefaultLandingHref(
        item: MenuItem,
        childrenByParent: Map<Long?, List<MenuItem>>,
        itemsById: Map<Long, MenuItem>,
    ): String? {
        val firstChild = childrenByParent[item.id]
            .orEmpty()
            .sortedWith(menuOrder)
            .firstOrNull()
            ?: return null

        return resolveHref(firstChild, childrenByParent, itemsById)
    }

    private fun resolveLinkType(item: MenuItem): NavigationLinkType =
        if (item.type == MenuType.EXTERNAL_LINK) NavigationLinkType.EXTERNAL else NavigationLinkType.INTERNAL

    private fun normalizeMatchPath(href: String): String? =
        href.takeIf { !it.startsWith("http://") && !it.startsWith("https://") }?.substringBefore('#')

    private fun buildStableHref(item: MenuItem, itemsById: Map<Long, MenuItem>): String =
        when (item.type) {
            MenuType.YOUTUBE_PLAYLIST -> PublicVideoMenuPathSupport.buildPlaylistPath(item, itemsById)
            else -> "/"
        }

    private fun buildMenuPath(item: MenuItem, itemsById: Map<Long, MenuItem>): String =
        buildPath(item, itemsById)

    private fun buildPath(
        item: MenuItem,
        itemsById: Map<Long, MenuItem>,
        prefix: String = "",
    ): String {
        val segments = mutableListOf<String>()
        var current: MenuItem? = item

        while (current != null) {
            segments += current.slug
            current = current.parentId?.let(itemsById::get)
        }

        return "$prefix/${segments.asReversed().joinToString("/")}"
    }

    private fun resolvePublishedMenu(
        path: String,
        childrenByParent: Map<Long?, List<MenuItem>>,
    ): MenuItem = resolvePublishedPath(path, childrenByParent)

    private fun normalizeLookupPath(path: String): String {
        val trimmed = path.substringBefore('?').substringBefore('#').trim()
        if (trimmed.isBlank()) {
            return "/"
        }
        return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }

    private fun resolvePublishedVideoMenu(
        path: String,
        childrenByParent: Map<Long?, List<MenuItem>>,
    ): MenuItem {
        val publishedItems = childrenByParent.values.flatten()
        val itemsById = publishedItems.associateBy { it.id!! }

        return publishedItems.firstOrNull { item ->
            item.type == MenuType.YOUTUBE_PLAYLIST &&
                PublicVideoMenuPathSupport.matchesPlaylistPath(item, itemsById, path)
        } ?: throw NotFoundException("공개된 재생목록을 찾을 수 없습니다. path=$path")
    }

    private fun resolvePublishedPath(
        path: String,
        childrenByParent: Map<Long?, List<MenuItem>>,
    ): MenuItem {
        val normalizedPath = normalizeLookupPath(path)
        val segments = normalizedPath.trim('/').split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) {
            throw NotFoundException("공개 메뉴를 찾을 수 없습니다. path=$path")
        }

        var parentId: Long? = null
        var current: MenuItem? = null

        for (segment in segments) {
            current = childrenByParent[parentId]
                .orEmpty()
                .firstOrNull { it.slug == segment }
                ?: throw NotFoundException("공개 메뉴를 찾을 수 없습니다. path=$path")
            parentId = current.id
        }

        return current ?: throw NotFoundException("공개 메뉴를 찾을 수 없습니다. path=$path")
    }
}

data class PublicNavigationResponse(
    val groups: List<NavigationGroupDto>,
)

enum class NavigationLinkType {
    INTERNAL,
    EXTERNAL,
}

data class NavigationItemDto(
    val key: String,
    val type: MenuType,
    val label: String,
    val href: String,
    val matchPath: String?,
    val linkType: NavigationLinkType,
    val contentSiteKey: String? = null,
    val openInNewTab: Boolean = false,
    val visible: Boolean,
    val headerVisible: Boolean,
    val mobileVisible: Boolean,
    val lnbVisible: Boolean,
    val breadcrumbVisible: Boolean,
    val defaultLanding: Boolean,
)

data class NavigationGroupDto(
    val key: String,
    val type: MenuType,
    val label: String,
    val href: String,
    val matchPath: String?,
    val linkType: NavigationLinkType,
    val contentSiteKey: String? = null,
    val openInNewTab: Boolean = false,
    val visible: Boolean,
    val headerVisible: Boolean,
    val mobileVisible: Boolean,
    val lnbVisible: Boolean,
    val breadcrumbVisible: Boolean,
    val defaultLandingHref: String?,
    val items: List<NavigationItemDto>,
)
