package org.happyzion.api.menu.domain

import org.happyzion.api.youtube.domain.YouTubeContentForm
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "menu_item")
class MenuItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "parent_id")
    var parentId: Long? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var type: MenuType,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: MenuStatus = MenuStatus.PUBLISHED,
    @Column(nullable = false, length = 100)
    var label: String,
    @Column(name = "label_customized", nullable = false)
    var labelCustomized: Boolean = false,
    @Column(name = "slug_customized", nullable = false)
    var slugCustomized: Boolean = false,
    @Column(nullable = false, length = 100)
    var slug: String,
    @Column(name = "static_page_key", length = 100)
    var staticPageKey: String? = null,
    @Column(name = "board_key", length = 100)
    var boardKey: String? = null,
    @Column(name = "playlist_id")
    var playlistId: Long? = null,
    @Column(name = "external_url")
    var externalUrl: String? = null,
    @Column(name = "open_in_new_tab", nullable = false)
    var openInNewTab: Boolean = false,
    @Column(nullable = false)
    var depth: Int = 0,
    @Column(nullable = false)
    var path: String = "",
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,
    @Column(name = "is_auto", nullable = false)
    var isAuto: Boolean = false,
    @Enumerated(EnumType.STRING)
    @Column(name = "playlist_content_form", length = 16)
    var playlistContentForm: YouTubeContentForm? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
