package org.happyzion.api.board.application

import org.happyzion.api.adminaccount.domain.AdminAccount
import org.happyzion.api.adminaccount.domain.AdminAccountRole
import org.happyzion.api.adminaccount.infrastructure.persistence.AdminAccountRepository
import org.happyzion.api.board.domain.Board
import org.happyzion.api.board.domain.Post
import org.happyzion.api.board.domain.PostAsset
import org.happyzion.api.board.infrastructure.persistence.BoardRepository
import org.happyzion.api.board.infrastructure.persistence.PostAssetRepository
import org.happyzion.api.board.infrastructure.persistence.PostRepository
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.menu.domain.MenuType
import org.happyzion.api.menu.infrastructure.persistence.MenuItemRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime

@Service
class BoardAdminService(
    private val boardRepository: BoardRepository,
    private val postRepository: PostRepository,
    private val postAssetRepository: PostAssetRepository,
    private val adminAccountRepository: AdminAccountRepository,
    private val menuItemRepository: MenuItemRepository,
    private val contentValidator: TiptapContentValidator = TiptapContentValidator(postAssetRepository),
    private val clock: Clock = Clock.systemUTC(),
) {

    @Transactional(readOnly = true)
    fun listBoards(actorId: Long): List<BoardAdminBoardSummary> {
        requireActiveAdmin(actorId)
        return boardRepository.findAll().map { board ->
            BoardAdminBoardSummary(
                id = board.id,
                slug = board.slug,
                title = board.title,
                type = board.type,
                description = board.description,
            )
        }
    }

    @Transactional(readOnly = true)
    fun listPosts(
        actorId: Long,
        boardSlug: String,
        menuId: Long? = null,
        page: Int = 0,
        size: Int = 20,
        title: String? = null,
    ): BoardAdminPostsPage {
        requireActiveAdmin(actorId)
        val board = requireBoard(boardSlug)
        val boardId = requireBoardId(board)
        menuId?.let { requireBoardMenu(boardSlug = board.slug, menuId = it) }
        val pageable = PageRequest.of(page, size)
        val normalizedTitle = title?.trim()?.takeIf { it.isNotEmpty() }

        val postsPage = when {
            normalizedTitle != null && menuId != null -> postRepository.findAdminPostsByBoardIdAndMenuIdAndTitle(
                boardId = boardId,
                menuId = menuId,
                title = normalizedTitle,
                pageable = pageable,
            )

            normalizedTitle != null -> postRepository.findAdminPostsByBoardIdAndTitle(
                boardId = boardId,
                title = normalizedTitle,
                pageable = pageable,
            )

            menuId != null -> postRepository.findAllByBoardIdAndMenuIdOrderByIsPinnedDescCreatedAtDescIdDesc(
                boardId = boardId,
                menuId = menuId,
                pageable = pageable,
            )

            else -> postRepository.findAllByBoardIdOrderByIsPinnedDescCreatedAtDescIdDesc(
                boardId = boardId,
                pageable = pageable,
            )
        }

        val authorNameById = adminAccountRepository.findAll()
            .filter { it.id != null }
            .associate { it.id!! to it.displayName }

        return BoardAdminPostsPage(
            posts = postsPage.content.map { post ->
                BoardAdminPostSummary(
                    id = post.id,
                    boardId = post.boardId,
                    menuId = post.menuId,
                    title = post.title,
                    isPublic = post.isPublic,
                    isPinned = post.isPinned,
                    authorId = post.authorId,
                    authorName = authorNameById[post.authorId] ?: "-",
                    createdAt = post.createdAt,
                    updatedAt = post.updatedAt,
                )
            },
            hasNext = postsPage.hasNext(),
        )
    }

    @Transactional(readOnly = true)
    fun getPost(actorId: Long, boardSlug: String, postId: Long, menuId: Long? = null): BoardAdminPostDetail {
        requireActiveAdmin(actorId)
        val board = requireBoard(boardSlug)
        val post = requirePostInBoard(postId, board, menuId)
        val assets = postAssetRepository.findAllByPostIdOrderBySortOrderAscIdAsc(postId)

        return BoardAdminPostDetail(
            id = post.id,
            boardId = post.boardId,
            menuId = post.menuId,
            title = post.title,
            contentJson = post.contentJson,
            contentHtml = post.contentHtml,
            isPublic = post.isPublic,
            isPinned = post.isPinned,
            authorId = post.authorId,
            createdAt = post.createdAt,
            updatedAt = post.updatedAt,
            assets = assets.map { asset ->
                BoardAdminPostAsset(
                    id = asset.id,
                    kind = asset.kind,
                    originalFilename = asset.originalFilename,
                    storedPath = asset.storedPath,
                    mimeType = asset.mimeType,
                    byteSize = asset.byteSize,
                    width = asset.width,
                    height = asset.height,
                    sortOrder = asset.sortOrder,
                )
            },
        )
    }

    @Transactional
    fun createPost(
        actorId: Long,
        boardSlug: String,
        command: BoardPostSaveCommand,
        menuId: Long? = command.menuId,
    ): BoardAdminPostSaveResult {
        requireActiveAdmin(actorId)
        val board = requireBoard(boardSlug)
        val boardMenuId = resolveBoardMenuId(board, menuId)
        val assetIds = mergeAssetIds(
            contentValidator.validate(
                contentJson = command.contentJson,
                actorId = actorId,
            ),
            command.assetIds,
        )

        val assets = resolveAssetsForCreate(
            assetIds = assetIds,
            actorId = actorId,
        )

        val saved = postRepository.save(
            Post(
                boardId = requireBoardId(board),
                menuId = boardMenuId,
                title = command.title,
                contentJson = command.contentJson,
                contentHtml = command.contentHtml,
                isPublic = command.isPublic,
                isPinned = command.isPinned,
                authorId = actorId,
            )
        )
        val postId = saved.id ?: throw IllegalStateException("저장된 게시글 id가 없습니다.")

        attachAssets(assets, assetIds, postId)

        return BoardAdminPostSaveResult(id = postId)
    }

    @Transactional
    fun updatePost(
        actorId: Long,
        boardSlug: String,
        postId: Long,
        command: BoardPostSaveCommand,
        menuId: Long? = command.menuId,
    ): BoardAdminPostSaveResult {
        val actor = requireActiveAdmin(actorId)
        requireBoard(boardSlug) // slug 유효성 확인용; 게시글은 postId 기준으로 조회
        val post = postRepository.findByIdOrNull(postId)
            ?: throw NotFoundException("게시글을 찾을 수 없습니다. id=$postId")
        requirePostEditPermission(actor, post)
        val savedPostId = post.id ?: throw IllegalStateException("게시글 id가 없습니다.")

        // menuId(게시판)가 변경된 경우 boardId도 함께 갱신
        val targetMenuId = menuId ?: post.menuId
        if (targetMenuId != post.menuId) {
            val targetBoardKey = menuItemRepository.findByIdOrNull(targetMenuId)?.boardKey
                ?: throw NotFoundException("메뉴를 찾을 수 없습니다. menuId=$targetMenuId")
            val targetBoardId = boardRepository.findBySlug(targetBoardKey)?.id
                ?: throw NotFoundException("게시판을 찾을 수 없습니다. slug=$targetBoardKey")
            post.menuId = targetMenuId
            post.boardId = targetBoardId
        }

        val assetIds = mergeAssetIds(
            contentValidator.validate(
                contentJson = command.contentJson,
                actorId = actorId,
                postId = savedPostId,
            ),
            command.assetIds,
        )
        val assets = resolveAssetsForUpdate(
            assetIds = assetIds,
            actorId = actorId,
            postId = savedPostId,
        )

        post.title = command.title
        post.contentJson = command.contentJson
        post.contentHtml = command.contentHtml
        post.isPublic = command.isPublic
        post.isPinned = command.isPinned
        val saved = postRepository.save(post)
        val resultId = saved.id ?: savedPostId

        syncAssets(assets, assetIds, resultId)

        return BoardAdminPostSaveResult(id = resultId)
    }

    @Transactional
    fun deletePost(actorId: Long, boardSlug: String, postId: Long, menuId: Long? = null) {
        val actor = requireActiveAdmin(actorId)
        val board = requireBoard(boardSlug)
        val post = requirePostInBoard(postId, board, menuId)
        requirePostEditPermission(actor, post)
        val savedPostId = post.id ?: throw IllegalStateException("게시글 id가 없습니다.")

        val detachedAt = OffsetDateTime.now(clock)
        val assets = postAssetRepository.findAllByPostIdOrderBySortOrderAscIdAsc(savedPostId)
            .map { asset ->
                asset.apply {
                    this.postId = null
                    this.detachedAt = detachedAt
                }
            }
        if (assets.isNotEmpty()) {
            postAssetRepository.saveAll(assets)
        }

        postRepository.delete(post)
    }

    private fun requireActiveAdmin(actorId: Long): AdminAccount {
        val actor = adminAccountRepository.findByIdOrNull(actorId)
            ?: throw NotFoundException("관리자 계정을 찾을 수 없습니다. id=$actorId")

        if (!actor.active) {
            throw ForbiddenException("비활성화된 계정은 게시판을 관리할 수 없습니다.")
        }

        return actor
    }

    private fun requirePostEditPermission(actor: AdminAccount, post: Post) {
        if (actor.role != AdminAccountRole.SUPER_ADMIN && post.authorId != actor.id) {
            throw ForbiddenException("본인이 작성한 게시글만 수정·삭제할 수 있습니다.")
        }
    }

    private fun requireBoard(slug: String): Board =
        boardRepository.findBySlug(slug)
            ?: throw NotFoundException("게시판을 찾을 수 없습니다. slug=$slug")

    private fun requireBoardId(board: Board): Long =
        board.id ?: throw IllegalStateException("게시판 id가 없습니다.")

    private fun requireBoardMenu(boardSlug: String, menuId: Long) {
        if (!menuItemRepository.existsByIdAndTypeAndBoardKey(menuId, MenuType.BOARD, boardSlug)) {
            throw NotFoundException("게시판 메뉴를 찾을 수 없습니다. menuId=$menuId")
        }
    }

    private fun resolveBoardMenuId(board: Board, menuId: Long?): Long {
        if (menuId != null) {
            requireBoardMenu(boardSlug = board.slug, menuId = menuId)
            return menuId
        }

        return menuItemRepository.findFirstByTypeAndBoardKeyOrderBySortOrderAscIdAsc(MenuType.BOARD, board.slug)?.id
            ?: throw NotFoundException("게시판 메뉴를 찾을 수 없습니다. slug=${board.slug}")
    }

    private fun requirePostInBoard(postId: Long, board: Board, menuId: Long? = null): Post {
        menuId?.let { requireBoardMenu(boardSlug = board.slug, menuId = it) }
        val post = postRepository.findByIdOrNull(postId)
            ?: throw NotFoundException("게시글을 찾을 수 없습니다. id=$postId")

        if (post.boardId != requireBoardId(board) || (menuId != null && post.menuId != menuId)) {
            throw NotFoundException("게시글을 찾을 수 없습니다. id=$postId")
        }

        return post
    }

    private fun mergeAssetIds(contentAssetIds: List<Long>, commandAssetIds: List<Long>): List<Long> =
        (contentAssetIds + commandAssetIds).distinct()

    private fun resolveAssetsForCreate(assetIds: List<Long>, actorId: Long): Map<Long, PostAsset> {
        if (assetIds.isEmpty()) {
            return emptyMap()
        }

        return resolveAssets(assetIds).also { assets ->
            assetIds.forEach { assetId ->
                val asset = assets.getValue(assetId)
                if (asset.uploadedByActorId != actorId || asset.postId != null) {
                    throw ForbiddenException("첨부 파일을 게시글에 연결할 수 없습니다. id=$assetId")
                }
            }
        }
    }

    private fun resolveAssetsForUpdate(assetIds: List<Long>, actorId: Long, postId: Long): Map<Long, PostAsset> {
        if (assetIds.isEmpty()) {
            return emptyMap()
        }

        return resolveAssets(assetIds).also { assets ->
            assetIds.forEach { assetId ->
                val asset = assets.getValue(assetId)
                if (asset.uploadedByActorId != actorId || (asset.postId != null && asset.postId != postId)) {
                    throw ForbiddenException("첨부 파일을 게시글에 연결할 수 없습니다. id=$assetId")
                }
            }
        }
    }

    private fun resolveAssets(assetIds: List<Long>): Map<Long, PostAsset> {
        val assetsById = postAssetRepository.findAllById(assetIds)
            .associateBy { it.id ?: throw IllegalStateException("첨부 파일 id가 없습니다.") }
            .toMutableMap()

        assetIds
            .filter { it !in assetsById }
            .forEach { assetId ->
                postAssetRepository.findByIdOrNull(assetId)?.let { asset ->
                    assetsById[assetId] = asset
                }
            }

        val missingIds = assetIds.filter { it !in assetsById }
        if (missingIds.isNotEmpty()) {
            throw NotFoundException("첨부 파일을 찾을 수 없습니다. ids=${missingIds.joinToString(",")}")
        }
        return assetsById
    }

    private fun attachAssets(assetsById: Map<Long, PostAsset>, assetIds: List<Long>, postId: Long) {
        if (assetIds.isEmpty()) {
            return
        }

        val attachedAssets = assetIds.mapIndexed { index, assetId ->
            assetsById.getValue(assetId).apply {
                this.postId = postId
                this.detachedAt = null
                this.sortOrder = index
            }
        }
        postAssetRepository.saveAll(attachedAssets)
    }

    private fun syncAssets(assetsById: Map<Long, PostAsset>, assetIds: List<Long>, postId: Long) {
        val selectedAssetIds = assetIds.toSet()
        val detachedAt = OffsetDateTime.now(clock)
        val detachedAssets = postAssetRepository.findAllByPostIdOrderBySortOrderAscIdAsc(postId)
            .filter { asset -> asset.id !in selectedAssetIds }
            .map { asset ->
                asset.apply {
                    this.postId = null
                    this.detachedAt = detachedAt
                    this.sortOrder = 0
                }
            }

        val attachedAssets = assetIds.mapIndexed { index, assetId ->
            assetsById.getValue(assetId).apply {
                this.postId = postId
                this.detachedAt = null
                this.sortOrder = index
            }
        }

        val changedAssets = detachedAssets + attachedAssets
        if (changedAssets.isNotEmpty()) {
            postAssetRepository.saveAll(changedAssets)
        }
    }
}
