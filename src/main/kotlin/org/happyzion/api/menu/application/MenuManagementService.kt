package org.happyzion.api.menu.application

import com.fasterxml.jackson.databind.ObjectMapper
import org.happyzion.api.adminaccount.infrastructure.persistence.AdminAccountRepository
import org.happyzion.api.board.domain.Board
import org.happyzion.api.board.domain.BoardType
import org.happyzion.api.board.infrastructure.persistence.BoardRepository
import org.happyzion.api.board.infrastructure.persistence.PostRepository
import org.happyzion.api.common.config.EvictPublicMenuCache
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.menu.domain.MenuItem
import org.happyzion.api.menu.domain.MenuRevision
import org.happyzion.api.menu.domain.MenuStatus
import org.happyzion.api.menu.domain.MenuType
import org.happyzion.api.menu.infrastructure.persistence.MenuItemRepository
import org.happyzion.api.menu.infrastructure.persistence.MenuRevisionRepository
import org.happyzion.api.youtube.application.PlaylistDisplayableVideoCountResolver
import org.happyzion.api.youtube.domain.YouTubeContentForm
import org.happyzion.api.youtube.infrastructure.persistence.YouTubePlaylistRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MenuManagementService(
    private val menuItemRepository: MenuItemRepository,
    private val menuRevisionRepository: MenuRevisionRepository,
    private val adminAccountRepository: AdminAccountRepository,
    private val boardRepository: BoardRepository,
    private val postRepository: PostRepository,
    private val youTubePlaylistRepository: YouTubePlaylistRepository,
    private val playlistDisplayableVideoCountResolver: PlaylistDisplayableVideoCountResolver,
    private val objectMapper: ObjectMapper,
) {
    private data class ResolvedSlug(
        val value: String,
        val customized: Boolean,
    )

    @Transactional(readOnly = true)
    fun getAdminSnapshot(actorId: Long): AdminMenuSnapshot {
        requireActiveAdmin(actorId)
        return AdminMenuSnapshot(items = buildAdminTree())
    }

    @Transactional
    @EvictPublicMenuCache
    fun replaceTree(actorId: Long, items: List<MenuTreeNodeInput>): AdminMenuSnapshot {
        requireActiveAdmin(actorId)

        val existingItems = menuItemRepository.findAllByOrderBySortOrderAscIdAsc()
        val existingById = existingItems.associateBy { it.id!! }
        val seenIds = linkedSetOf<Long>()
        val pathRecomputeRootIds = linkedSetOf<Long>()

        validateInputTree(items)
        persistNodes(
            nodes = items,
            parentId = null,
            depth = 0,
            parentType = null,
            existingById = existingById,
            seenIds = seenIds,
            pathRecomputeRootIds = pathRecomputeRootIds,
        )

        val removedItems = existingItems.filter { it.id !in seenIds }
        if (removedItems.any { it.isAuto }) {
            throw IllegalArgumentException("자동 생성 메뉴는 트리에서 제거할 수 없습니다.")
        }

        val removedIds = removedItems.mapNotNull { it.id }.toSet()
        val rootRemovedIds = removedItems
            .filter { it.parentId == null || it.parentId !in removedIds }
            .mapNotNull { it.id }
        if (rootRemovedIds.isNotEmpty()) {
            deleteBoardsScopedToMenus(removedIds)
            menuItemRepository.deleteAllByIdInBatch(rootRemovedIds)
        }

        val allPersisted = menuItemRepository.findAllByOrderBySortOrderAscIdAsc()
        recomputePaths(allPersisted, pathRecomputeRootIds)

        val snapshot = AdminMenuSnapshot(items = buildAdminTree())
        menuRevisionRepository.save(
            MenuRevision(
                snapshot = objectMapper.writeValueAsString(snapshot),
                summary = "메뉴 트리 일괄 저장",
                createdBy = actorId,
            )
        )

        return snapshot
    }

    @Transactional
    @EvictPublicMenuCache
    fun deleteMenuItem(actorId: Long, menuId: Long) {
        requireActiveAdmin(actorId)

        val menu = menuItemRepository.findByIdOrNull(menuId)
            ?: throw NotFoundException("메뉴를 찾을 수 없습니다. id=$menuId")

        if (menu.isAuto) {
            throw IllegalArgumentException("자동 생성 메뉴는 수동 삭제할 수 없습니다.")
        }

        val items = menuItemRepository.findAllByOrderBySortOrderAscIdAsc()
        deleteBoardsScopedToMenus(collectMenuSubtreeIds(menuId, items))
        menuItemRepository.delete(menu)
    }

    private fun collectMenuSubtreeIds(rootId: Long, items: List<MenuItem>): Set<Long> {
        val ids = linkedSetOf<Long>()
        val childrenByParent = items.groupBy { it.parentId }

        fun visit(menuId: Long) {
            if (!ids.add(menuId)) {
                return
            }
            childrenByParent[menuId].orEmpty().forEach { child ->
                child.id?.let(::visit)
            }
        }

        visit(rootId)
        return ids
    }

    private fun deleteBoardsScopedToMenus(menuIds: Set<Long>) {
        if (menuIds.isEmpty()) {
            return
        }

        val boards = boardRepository.findAllByMenuIdIn(menuIds)
        if (boards.isNotEmpty()) {
            boardRepository.deleteAll(boards)
        }
    }

    private fun validateInputTree(nodes: List<MenuTreeNodeInput>) {
        val staticPageKeys = linkedSetOf<String>()

        fun visitSiblings(siblings: List<MenuTreeNodeInput>, depth: Int, parentType: MenuType?) {
            val siblingSlugs = linkedSetOf<String>()

            siblings.forEach { node ->
                val normalizedSlug = normalizeExplicitSlug(node.slug)
                if (normalizedSlug != null && !siblingSlugs.add(normalizedSlug)) {
                    throw IllegalArgumentException("같은 메뉴 아래에 중복된 slug가 있습니다: $normalizedSlug")
                }

                if (node.status == MenuStatus.ARCHIVED && !node.isAuto) {
                    throw IllegalArgumentException("ARCHIVED 상태는 자동 유튜브 메뉴에만 사용할 수 있습니다.")
                }

                if (node.status == MenuStatus.DRAFT && !node.isAuto) {
                    throw IllegalArgumentException("DRAFT 상태는 자동 유튜브 메뉴 최초 동기화에만 사용할 수 있습니다.")
                }

                validatePlacement(
                    type = node.type,
                    depth = depth,
                    parentType = parentType,
                    isAuto = node.isAuto,
                    status = node.status,
                    hasChildren = node.children.isNotEmpty(),
                )

                if (node.type == MenuType.STATIC) {
                    val staticPageKey = node.staticPageKey?.trim()
                    if (!staticPageKey.isNullOrBlank() && !staticPageKeys.add(staticPageKey)) {
                        throw IllegalArgumentException("하나의 정적 페이지는 하나의 메뉴에만 연결할 수 있습니다: $staticPageKey")
                    }
                }

                visitSiblings(node.children, depth + 1, node.type)
            }
        }

        visitSiblings(nodes, depth = 0, parentType = null)
    }

    private fun persistNodes(
        nodes: List<MenuTreeNodeInput>,
        parentId: Long?,
        depth: Int,
        parentType: MenuType?,
        existingById: Map<Long, MenuItem>,
        seenIds: MutableSet<Long>,
        pathRecomputeRootIds: MutableSet<Long>,
    ): List<MenuItem> =
        nodes.mapIndexed { index, node ->
            val item = node.id?.let(existingById::get)
                ?: MenuItem(
                    type = node.type,
                    label = node.label.trim(),
                    slug = "",
                )

            val menuId = item.id
            val previousParentId = item.parentId
            if (menuId != null && !seenIds.add(menuId)) {
                throw IllegalArgumentException("동일한 메뉴가 중복 포함되어 있습니다. id=$menuId")
            }

            if (item.isAuto && node.type != item.type) {
                throw IllegalArgumentException("자동 생성 메뉴의 타입은 변경할 수 없습니다.")
            }

            val normalizedLabel = node.label.trim()
            if (normalizedLabel.isBlank()) {
                throw IllegalArgumentException("메뉴 이름은 비어 있을 수 없습니다.")
            }

            val playlistSourceTitle = if (item.type == MenuType.YOUTUBE_PLAYLIST) {
                item.playlistId?.let { playlistId ->
                    youTubePlaylistRepository.findByIdOrNull(playlistId)?.title
                }
            } else {
                null
            }

            val resolvedSlug = resolveEffectiveSlug(
                node = node,
                item = item,
                parentId = parentId,
                autoSlugBaseLabel = playlistSourceTitle,
            )

            val resolvedType = if (item.isAuto) item.type else node.type
            validatePlacement(
                type = resolvedType,
                depth = depth,
                parentType = parentType,
                isAuto = item.isAuto,
                status = node.status,
                hasChildren = node.children.isNotEmpty(),
            )

            val isNew = item.id == null
            item.updateField(item.parentId, parentId) { item.parentId = it }
            item.updateField(item.type, resolvedType) { item.type = it }
            item.updateField(item.status, normalizeStatus(node, item, parentId)) { item.status = it }
            item.updateField(item.label, normalizedLabel) { item.label = it }
            item.updateField(item.slug, resolvedSlug.value) { item.slug = it }
            item.updateField(item.slugCustomized, resolvedSlug.customized) { item.slugCustomized = it }
            item.updateField(item.sortOrder, index) { item.sortOrder = it }
            item.updateField(item.depth, depth) { item.depth = it }
            item.updateField(item.openInNewTab, node.openInNewTab) { item.openInNewTab = it }

            when (item.type) {
                MenuType.STATIC -> {
                    val staticPageKey = node.staticPageKey?.trim()
                    if (staticPageKey.isNullOrBlank()) {
                        throw IllegalArgumentException("정적 페이지 메뉴는 staticPageKey가 필요합니다.")
                    }
                    if (staticPageKey !in StaticPageCatalog.allKeys()) {
                        throw IllegalArgumentException("지원하지 않는 staticPageKey 입니다: $staticPageKey")
                    }
                    item.updateField(item.staticPageKey, staticPageKey) { item.staticPageKey = it }
                    item.updateField(item.boardKey, null) { item.boardKey = it }
                    item.updateField(item.externalUrl, null) { item.externalUrl = it }
                    item.updateField(item.openInNewTab, false) { item.openInNewTab = it }
                    item.updateField(item.playlistContentForm, null) { item.playlistContentForm = it }
                }

                MenuType.BOARD -> {
                    val boardKey = node.boardKey?.trim()?.takeIf { it.isNotBlank() } ?: item.boardKey
                    item.updateField(item.boardKey, boardKey) { item.boardKey = it }
                    item.updateField(item.staticPageKey, null) { item.staticPageKey = it }
                    item.updateField(item.externalUrl, null) { item.externalUrl = it }
                    item.updateField(item.openInNewTab, false) { item.openInNewTab = it }
                    item.updateField(item.playlistContentForm, null) { item.playlistContentForm = it }
                }

                MenuType.EXTERNAL_LINK -> {
                    val externalUrl = node.externalUrl?.trim()
                    if (externalUrl.isNullOrBlank()) {
                        throw IllegalArgumentException("외부 링크 메뉴는 URL이 필요합니다.")
                    }
                    item.updateField(item.externalUrl, externalUrl) { item.externalUrl = it }
                    item.updateField(item.staticPageKey, null) { item.staticPageKey = it }
                    item.updateField(item.boardKey, null) { item.boardKey = it }
                    item.updateField(item.playlistContentForm, null) { item.playlistContentForm = it }
                }

                MenuType.FOLDER,
                MenuType.YOUTUBE_PLAYLIST_GROUP -> {
                    item.updateField(item.staticPageKey, null) { item.staticPageKey = it }
                    item.updateField(item.boardKey, null) { item.boardKey = it }
                    item.updateField(item.externalUrl, null) { item.externalUrl = it }
                    item.updateField(item.openInNewTab, false) { item.openInNewTab = it }
                    item.updateField(item.playlistContentForm, null) { item.playlistContentForm = it }
                }

                MenuType.YOUTUBE_PLAYLIST -> {
                    if (!item.isAuto) {
                        throw IllegalArgumentException("유튜브 재생목록 메뉴는 동기화로만 생성할 수 있습니다.")
                    }
                    item.updateField(item.staticPageKey, null) { item.staticPageKey = it }
                    item.updateField(item.boardKey, null) { item.boardKey = it }
                    item.updateField(item.externalUrl, null) { item.externalUrl = it }
                    item.updateField(item.openInNewTab, false) { item.openInNewTab = it }
                    item.updateField(
                        item.playlistContentForm,
                        node.playlistContentForm ?: item.playlistContentForm ?: YouTubeContentForm.LONGFORM,
                    ) { item.playlistContentForm = it }
                    item.updateField(
                        item.labelCustomized,
                        playlistSourceTitle?.let { sourceTitle -> normalizedLabel != sourceTitle } ?: false,
                    ) { item.labelCustomized = it }
                }
            }

            val saved = if (isNew) menuItemRepository.save(item) else item
            if (saved.id != null) {
                seenIds.add(saved.id)
                if (isNew || previousParentId != parentId) {
                    pathRecomputeRootIds.add(saved.id)
                }
            }
            if (saved.type == MenuType.BOARD) {
                ensureMenuScopedBoard(saved, node.boardType)
            }
            persistNodes(
                nodes = node.children,
                parentId = saved.id,
                depth = depth + 1,
                parentType = saved.type,
                existingById = existingById + (saved.id!! to saved),
                seenIds = seenIds,
                pathRecomputeRootIds = pathRecomputeRootIds,
            )
            saved
        }

    private fun normalizeStatus(node: MenuTreeNodeInput, existing: MenuItem, parentId: Long?): MenuStatus {
        if (existing.isAuto && existing.status == MenuStatus.ARCHIVED && node.status != MenuStatus.ARCHIVED) {
            throw IllegalArgumentException("ARCHIVED 상태는 유튜브 동기화로만 해제됩니다.")
        }

        if (!existing.isAuto && node.status == MenuStatus.DRAFT) {
            throw IllegalArgumentException("DRAFT 상태는 자동 유튜브 메뉴 최초 동기화에만 사용할 수 있습니다.")
        }

        if (existing.type == MenuType.YOUTUBE_PLAYLIST && node.status == MenuStatus.PUBLISHED && parentId == null) {
            throw IllegalArgumentException("유튜브 재생목록은 그룹을 지정한 뒤에만 노출할 수 있습니다.")
        }

        return when {
            existing.isAuto -> node.status
            node.status == MenuStatus.ARCHIVED -> throw IllegalArgumentException("ARCHIVED 상태는 자동 유튜브 메뉴에만 사용할 수 있습니다.")
            else -> node.status
        }
    }

    private fun validatePlacement(
        type: MenuType,
        depth: Int,
        parentType: MenuType?,
        isAuto: Boolean,
        status: MenuStatus,
        hasChildren: Boolean,
    ) {
        if (depth > 1) {
            throw IllegalArgumentException("메뉴는 GNB/LNB 2단계까지만 구성할 수 있습니다.")
        }

        when (depth) {
            0 -> when (type) {
                MenuType.FOLDER,
                MenuType.YOUTUBE_PLAYLIST_GROUP -> Unit

                MenuType.YOUTUBE_PLAYLIST -> {
                    if (!isAuto) {
                        throw IllegalArgumentException("유튜브 재생목록 메뉴는 동기화로만 생성할 수 있습니다.")
                    }
                    if (status == MenuStatus.PUBLISHED) {
                        throw IllegalArgumentException("유튜브 재생목록은 그룹을 지정한 뒤에만 노출할 수 있습니다.")
                    }
                }

                else -> throw IllegalArgumentException("GNB는 일반 메뉴 그룹 또는 영상 그룹으로만 만들 수 있습니다.")
            }

            1 -> when (parentType) {
                MenuType.FOLDER -> {
                    if (type !in setOf(MenuType.STATIC, MenuType.BOARD, MenuType.EXTERNAL_LINK)) {
                        throw IllegalArgumentException("일반 메뉴 그룹 아래에는 정적 페이지, 게시판, 외부 링크만 둘 수 있습니다.")
                    }
                }

                MenuType.YOUTUBE_PLAYLIST_GROUP -> {
                    if (type != MenuType.YOUTUBE_PLAYLIST) {
                        throw IllegalArgumentException("영상 그룹 아래에는 유튜브 재생목록만 둘 수 있습니다.")
                    }
                }

                else -> throw IllegalArgumentException("하위 메뉴를 둘 수 없는 상위 타입입니다.")
            }
        }

        if (type in setOf(MenuType.STATIC, MenuType.BOARD, MenuType.EXTERNAL_LINK, MenuType.YOUTUBE_PLAYLIST) && hasChildren) {
            throw IllegalArgumentException("링크 메뉴는 하위 메뉴를 가질 수 없습니다.")
        }
    }

    private fun recomputePaths(items: List<MenuItem>, rootIds: Set<Long>) {
        if (rootIds.isEmpty()) {
            return
        }

        val itemsById = items.associateBy { it.id!! }
        val itemsByParent = items.groupBy { it.parentId }
        val topLevelRootIds = rootIds.filter { rootId ->
            generateSequence(itemsById[rootId]?.parentId) { parentId -> itemsById[parentId]?.parentId }
                .none { parentId -> parentId in rootIds }
        }

        fun visit(item: MenuItem, parentPath: String, depth: Int) {
            val itemId = item.id ?: return
            val nextPath = "$parentPath$itemId/"
            item.updateField(item.depth, depth) { item.depth = it }
            item.updateField(item.path, nextPath) { item.path = it }

            itemsByParent[itemId]
                .orEmpty()
                .sortedWith(compareBy<MenuItem> { it.sortOrder }.thenBy { it.id })
                .forEach { child -> visit(child, item.path, depth + 1) }
        }

        topLevelRootIds
            .mapNotNull(itemsById::get)
            .sortedWith(compareBy<MenuItem> { it.depth }.thenBy { it.sortOrder }.thenBy { it.id })
            .forEach { root ->
                val parent = root.parentId?.let(itemsById::get)
                visit(
                    item = root,
                    parentPath = parent?.path ?: "/",
                    depth = parent?.let { it.depth + 1 } ?: 0,
                )
            }
    }

    private fun buildAdminTree(): List<MenuTreeNode> {
        val items = menuItemRepository.findAllByOrderBySortOrderAscIdAsc()
        val playlistsById = youTubePlaylistRepository.findAll().associateBy { it.id!! }
        val displayableCountsByPlaylistId = playlistDisplayableVideoCountResolver.resolveAll(playlistsById.keys)
        val boardsBySlug = boardRepository.findAll().associateBy { it.slug }
        val childrenByParent = items.groupBy { it.parentId }

        fun build(parentId: Long?): List<MenuTreeNode> =
            childrenByParent[parentId]
                .orEmpty()
                .sortedWith(compareBy<MenuItem> { it.sortOrder }.thenBy { it.id })
                .map { item ->
                    val playlist = item.playlistId?.let(playlistsById::get)
                    val board = item.boardKey?.let(boardsBySlug::get)
                    MenuTreeNode(
                        id = item.id!!,
                        type = item.type,
                        status = item.status,
                        label = item.label,
                        slug = item.slug,
                        isAuto = item.isAuto,
                        labelCustomized = item.labelCustomized,
                        slugCustomized = item.slugCustomized,
                        staticPageKey = item.staticPageKey,
                        boardKey = item.boardKey,
                        boardTypeKey = board?.type?.name,
                        boardTypeLabel = board?.type?.label,
                        externalUrl = item.externalUrl,
                        openInNewTab = item.openInNewTab,
                        playlistTitle = playlist?.title,
                        playlistSourceTitle = playlist?.title,
                        thumbnailUrl = playlist?.thumbnailUrl,
                        itemCount = playlist?.id?.let { displayableCountsByPlaylistId[it] ?: 0 },
                        syncStatus = playlist?.syncStatus,
                        playlistContentForm = item.playlistContentForm,
                        parentId = item.parentId,
                        children = build(item.id),
                    )
                }

        return build(parentId = null)
    }

    private fun ensureMenuScopedBoard(menu: MenuItem, requestedBoardType: BoardType?) {
        val menuId = menu.id ?: throw IllegalStateException("게시판 메뉴 id가 없습니다.")
        val previousBoard = menu.boardKey?.let(boardRepository::findBySlug)
        val boardType = requestedBoardType
            ?: previousBoard?.type
            ?: BoardType.GENERAL
        val board = boardRepository.findByMenuId(menuId) ?: previousBoard ?: Board(
            slug = buildBoardSlug(menu, currentBoardId = null),
            menuId = menuId,
            title = menu.label,
            type = boardType,
            description = null,
        )
        val isNewBoard = board.id == null

        updateField(board.slug, buildBoardSlug(menu, currentBoardId = board.id)) { board.slug = it }
        updateField(board.menuId, menuId) { board.menuId = it }
        updateField(board.title, menu.label) { board.title = it }
        updateField(board.type, boardType) { board.type = it }
        updateField(board.description, null) { board.description = it }

        val savedBoard = if (isNewBoard) boardRepository.save(board) else board
        val savedBoardId = savedBoard.id ?: throw IllegalStateException("게시판 id가 없습니다.")

        if (menu.boardKey != savedBoard.slug) {
            menu.boardKey = savedBoard.slug
        }
        postRepository.updateBoardIdByMenuId(menuId = menuId, boardId = savedBoardId)
    }

    private fun buildBoardSlug(menu: MenuItem, currentBoardId: Long?): String {
        val parentSlug = menu.parentId
            ?.let { menuItemRepository.findByIdOrNull(it) }
            ?.slug
            ?.takeIf { it.isNotBlank() }
        val base = listOfNotNull(parentSlug, menu.slug.takeIf { it.isNotBlank() })
            .joinToString("-")
            .ifBlank { "board" }
        var candidate = base
        var sequence = 1

        while (true) {
            val existing = boardRepository.findBySlug(candidate)
            if (existing == null || existing.id == currentBoardId) {
                return candidate
            }
            sequence += 1
            candidate = "$base-$sequence"
        }
    }

    private fun ensureSlugAvailable(slug: String, currentId: Long?, parentId: Long?) {
        if (!isSlugAvailable(slug, currentId, parentId)) {
            throw IllegalArgumentException("같은 메뉴 아래에서 이미 사용 중인 slug 입니다: $slug")
        }
    }

    private fun isSlugAvailable(slug: String, currentId: Long?, parentId: Long?): Boolean {
        val existing = if (parentId == null) {
            menuItemRepository.findRootBySlug(slug)
        } else {
            menuItemRepository.findByParentIdAndSlug(parentId, slug)
        }
        return existing == null || existing.id == currentId
    }

    private fun resolveEffectiveSlug(
        node: MenuTreeNodeInput,
        item: MenuItem,
        parentId: Long?,
        autoSlugBaseLabel: String? = null,
    ): ResolvedSlug =
        when {
            item.isAuto -> resolveAutoSlug(node, item, parentId, autoSlugBaseLabel)
            node.slug.isBlank() -> ResolvedSlug(
                value = generateAvailableSlug(node.label, item.id, parentId),
                customized = false,
            )
            else -> normalizeExplicitSlug(node.slug)?.also { normalized ->
                ensureSlugAvailable(normalized, item.id, parentId)
            }?.let { normalized ->
                ResolvedSlug(value = normalized, customized = false)
            } ?: throw IllegalArgumentException("slug를 생성할 수 없습니다.")
        }

    private fun resolveAutoSlug(
        node: MenuTreeNodeInput,
        item: MenuItem,
        parentId: Long?,
        autoSlugBaseLabel: String?,
    ): ResolvedSlug {
        if (node.slugCustomized && node.slug.isNotBlank()) {
            val normalized = normalizeExplicitSlug(node.slug)
                ?: throw IllegalArgumentException("slug를 생성할 수 없습니다.")
            ensureSlugAvailable(normalized, item.id, parentId)
            return ResolvedSlug(value = normalized, customized = true)
        }

        return ResolvedSlug(
            value = generateAvailableSlug(
                rawLabel = autoSlugBaseLabel?.takeIf { it.isNotBlank() } ?: node.label,
                currentId = item.id,
                parentId = parentId,
            ),
            customized = false,
        )
    }

    private fun generateAvailableSlug(rawLabel: String, currentId: Long?, parentId: Long?): String {
        val base = MenuSlugSupport.slugifyToAscii(rawLabel).ifBlank { "menu" }
        var sequence = 1
        var candidate = base

        while (!isSlugAvailable(candidate, currentId, parentId)) {
            sequence += 1
            candidate = "$base-$sequence"
        }

        return candidate
    }

    private fun normalizeExplicitSlug(rawSlug: String?): String? {
        val trimmed = rawSlug?.trim() ?: return null
        if (trimmed.isBlank()) {
            return null
        }

        val normalized = MenuSlugSupport.slugifyToAscii(trimmed)
        if (normalized.isBlank()) {
            throw IllegalArgumentException("slug는 영문, 숫자, 하이픈으로 구성할 수 있는 값이어야 합니다.")
        }
        return normalized
    }

    private fun requireActiveAdmin(actorId: Long) {
        val actor = adminAccountRepository.findByIdOrNull(actorId)
            ?: throw NotFoundException("관리자 계정을 찾을 수 없습니다. id=$actorId")

        if (!actor.active) {
            throw ForbiddenException("비활성화된 계정은 메뉴를 관리할 수 없습니다.")
        }
    }

    private inline fun <T> MenuItem.updateField(current: T, next: T, apply: (T) -> Unit) {
        updateFieldValue(current, next, apply)
    }

    private inline fun <T> updateField(current: T, next: T, apply: (T) -> Unit) {
        updateFieldValue(current, next, apply)
    }

    private inline fun <T> updateFieldValue(current: T, next: T, apply: (T) -> Unit) {
        if (current != next) {
            apply(next)
        }
    }
}
