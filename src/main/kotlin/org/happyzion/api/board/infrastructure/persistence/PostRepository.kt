package org.happyzion.api.board.infrastructure.persistence

import org.happyzion.api.board.domain.Post
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PostRepository : JpaRepository<Post, Long> {
    fun findAllByBoardIdOrderByIsPinnedDescCreatedAtDescIdDesc(boardId: Long): List<Post>
    fun findAllByBoardIdAndMenuIdOrderByIsPinnedDescCreatedAtDescIdDesc(boardId: Long, menuId: Long): List<Post>
    fun findAllByBoardIdAndIsPublicTrueOrderByIsPinnedDescCreatedAtDescIdDesc(boardId: Long, pageable: Pageable): Page<Post>
    fun findAllByMenuIdAndIsPublicTrueOrderByIsPinnedDescCreatedAtDescIdDesc(menuId: Long, pageable: Pageable): Page<Post>
    fun findAllByBoardIdOrderByIsPinnedDescCreatedAtDescIdDesc(boardId: Long, pageable: Pageable): Page<Post>
    fun findAllByBoardIdAndMenuIdOrderByIsPinnedDescCreatedAtDescIdDesc(
        boardId: Long,
        menuId: Long,
        pageable: Pageable,
    ): Page<Post>
    fun findByBoardIdAndIdAndIsPublicTrue(boardId: Long, id: Long): Post?
    fun findByMenuIdAndIdAndIsPublicTrue(menuId: Long, id: Long): Post?

    @Query(
        value = """
            SELECT * FROM post
            WHERE board_id = :boardId
            AND is_public = true
            AND (
                (:isPinned = false AND is_pinned = true)
                OR (
                    is_pinned = :isPinned
                    AND (created_at > :createdAt OR (created_at = :createdAt AND id > :id))
                )
            )
            ORDER BY is_pinned ASC, created_at ASC, id ASC
            LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findPreviousPublicPostByBoardId(
        @Param("boardId") boardId: Long,
        @Param("isPinned") isPinned: Boolean,
        @Param("createdAt") createdAt: java.time.OffsetDateTime,
        @Param("id") id: Long,
    ): Post?

    @Query(
        value = """
            SELECT * FROM post
            WHERE board_id = :boardId
            AND is_public = true
            AND (
                (:isPinned = true AND is_pinned = false)
                OR (
                    is_pinned = :isPinned
                    AND (created_at < :createdAt OR (created_at = :createdAt AND id < :id))
                )
            )
            ORDER BY is_pinned DESC, created_at DESC, id DESC
            LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findNextPublicPostByBoardId(
        @Param("boardId") boardId: Long,
        @Param("isPinned") isPinned: Boolean,
        @Param("createdAt") createdAt: java.time.OffsetDateTime,
        @Param("id") id: Long,
    ): Post?

    @Query(
        value = """
            SELECT * FROM post
            WHERE menu_id = :menuId
            AND is_public = true
            AND (
                (:isPinned = false AND is_pinned = true)
                OR (
                    is_pinned = :isPinned
                    AND (created_at > :createdAt OR (created_at = :createdAt AND id > :id))
                )
            )
            ORDER BY is_pinned ASC, created_at ASC, id ASC
            LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findPreviousPublicPostByMenuId(
        @Param("menuId") menuId: Long,
        @Param("isPinned") isPinned: Boolean,
        @Param("createdAt") createdAt: java.time.OffsetDateTime,
        @Param("id") id: Long,
    ): Post?

    @Query(
        value = """
            SELECT * FROM post
            WHERE menu_id = :menuId
            AND is_public = true
            AND (
                (:isPinned = true AND is_pinned = false)
                OR (
                    is_pinned = :isPinned
                    AND (created_at < :createdAt OR (created_at = :createdAt AND id < :id))
                )
            )
            ORDER BY is_pinned DESC, created_at DESC, id DESC
            LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findNextPublicPostByMenuId(
        @Param("menuId") menuId: Long,
        @Param("isPinned") isPinned: Boolean,
        @Param("createdAt") createdAt: java.time.OffsetDateTime,
        @Param("id") id: Long,
    ): Post?

    @Query(
        value = """
            SELECT p FROM Post p
            WHERE p.boardId = :boardId
            AND p.isPublic = true
            AND LOWER(p.title) LIKE LOWER(CONCAT('%', :title, '%'))
            ORDER BY p.isPinned DESC, p.createdAt DESC, p.id DESC
        """,
        countQuery = """
            SELECT COUNT(p) FROM Post p
            WHERE p.boardId = :boardId
            AND p.isPublic = true
            AND LOWER(p.title) LIKE LOWER(CONCAT('%', :title, '%'))
        """,
    )
    fun findPublicPostsByBoardIdAndTitle(
        @Param("boardId") boardId: Long,
        @Param("title") title: String,
        pageable: Pageable,
    ): Page<Post>

    @Query(
        value = """
            SELECT p FROM Post p
            WHERE p.menuId = :menuId
            AND p.isPublic = true
            AND LOWER(p.title) LIKE LOWER(CONCAT('%', :title, '%'))
            ORDER BY p.isPinned DESC, p.createdAt DESC, p.id DESC
        """,
        countQuery = """
            SELECT COUNT(p) FROM Post p
            WHERE p.menuId = :menuId
            AND p.isPublic = true
            AND LOWER(p.title) LIKE LOWER(CONCAT('%', :title, '%'))
        """,
    )
    fun findPublicPostsByMenuIdAndTitle(
        @Param("menuId") menuId: Long,
        @Param("title") title: String,
        pageable: Pageable,
    ): Page<Post>

    @Query(
        value = """
            SELECT p FROM Post p
            WHERE p.boardId = :boardId
            AND LOWER(p.title) LIKE LOWER(CONCAT('%', :title, '%'))
            ORDER BY p.isPinned DESC, p.createdAt DESC, p.id DESC
        """,
        countQuery = """
            SELECT COUNT(p) FROM Post p
            WHERE p.boardId = :boardId
            AND LOWER(p.title) LIKE LOWER(CONCAT('%', :title, '%'))
        """,
    )
    fun findAdminPostsByBoardIdAndTitle(
        @Param("boardId") boardId: Long,
        @Param("title") title: String,
        pageable: Pageable,
    ): Page<Post>

    @Query(
        value = """
            SELECT p FROM Post p
            WHERE p.boardId = :boardId
            AND p.menuId = :menuId
            AND LOWER(p.title) LIKE LOWER(CONCAT('%', :title, '%'))
            ORDER BY p.isPinned DESC, p.createdAt DESC, p.id DESC
        """,
        countQuery = """
            SELECT COUNT(p) FROM Post p
            WHERE p.boardId = :boardId
            AND p.menuId = :menuId
            AND LOWER(p.title) LIKE LOWER(CONCAT('%', :title, '%'))
        """,
    )
    fun findAdminPostsByBoardIdAndMenuIdAndTitle(
        @Param("boardId") boardId: Long,
        @Param("menuId") menuId: Long,
        @Param("title") title: String,
        pageable: Pageable,
    ): Page<Post>

    @Modifying
    @Query("update Post post set post.boardId = :boardId where post.menuId = :menuId")
    fun updateBoardIdByMenuId(@Param("menuId") menuId: Long, @Param("boardId") boardId: Long): Int
}
