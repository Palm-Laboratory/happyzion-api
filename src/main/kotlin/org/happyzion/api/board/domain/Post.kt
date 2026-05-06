package org.happyzion.api.board.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(name = "post")
class Post(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "board_id", nullable = false)
    var boardId: Long,
    @Column(name = "menu_id", nullable = false)
    var menuId: Long = boardId,
    @Column(nullable = false, length = 200)
    var title: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", nullable = false, columnDefinition = "jsonb")
    var contentJson: String,
    @Column(name = "author_id", nullable = false)
    var authorId: Long,
    @Column(name = "content_html")
    var contentHtml: String? = null,
    @Column(name = "published_at")
    var publishedAt: OffsetDateTime? = null,
    @Column(name = "is_public", nullable = false)
    var isPublic: Boolean = true,
    @Column(name = "is_pinned", nullable = false)
    var isPinned: Boolean = false,
    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 0,
    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
