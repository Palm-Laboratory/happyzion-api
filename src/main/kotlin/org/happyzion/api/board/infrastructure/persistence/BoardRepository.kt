package org.happyzion.api.board.infrastructure.persistence

import org.happyzion.api.board.domain.Board
import org.springframework.data.jpa.repository.JpaRepository

interface BoardRepository : JpaRepository<Board, Long> {
    fun findBySlug(slug: String): Board?
    fun findByMenuId(menuId: Long): Board?
    fun findAllByMenuIdIn(menuIds: Collection<Long>): List<Board>
}
