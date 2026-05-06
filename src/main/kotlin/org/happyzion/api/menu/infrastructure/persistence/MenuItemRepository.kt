package org.happyzion.api.menu.infrastructure.persistence

import org.happyzion.api.menu.domain.MenuItem
import org.happyzion.api.menu.domain.MenuStatus
import org.happyzion.api.menu.domain.MenuType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MenuItemRepository : JpaRepository<MenuItem, Long> {
    fun findAllByOrderBySortOrderAscIdAsc(): List<MenuItem>
    fun findAllByStatusOrderBySortOrderAscIdAsc(status: MenuStatus): List<MenuItem>
    fun findBySlug(slug: String): MenuItem?
    fun findByTypeAndStatusAndSlug(type: org.happyzion.api.menu.domain.MenuType, status: MenuStatus, slug: String): MenuItem?
    fun findFirstByTypeAndBoardKeyOrderBySortOrderAscIdAsc(type: MenuType, boardKey: String): MenuItem?
    fun existsByTypeAndStatusAndBoardKey(type: MenuType, status: MenuStatus, boardKey: String): Boolean
    fun existsByIdAndTypeAndBoardKey(id: Long, type: MenuType, boardKey: String): Boolean
    fun existsByIdAndTypeAndStatusAndBoardKey(id: Long, type: MenuType, status: MenuStatus, boardKey: String): Boolean
    fun existsBySlugAndIdNot(slug: String, id: Long): Boolean
    fun findByParentIdAndSlug(parentId: Long, slug: String): MenuItem?

    @Query("select m from MenuItem m where m.parentId is null and m.slug = :slug")
    fun findRootBySlug(@Param("slug") slug: String): MenuItem?
}
