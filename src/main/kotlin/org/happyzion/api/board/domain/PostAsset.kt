package org.happyzion.api.board.domain

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
@Table(name = "post_asset")
class PostAsset(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "uploaded_by_actor_id", nullable = false)
    var uploadedByActorId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var kind: PostAssetKind,
    @Column(name = "original_filename", nullable = false, length = 255)
    var originalFilename: String,
    @Column(name = "stored_path", nullable = false)
    var storedPath: String,
    @Column(name = "byte_size", nullable = false)
    var byteSize: Long,
    @Column(name = "post_id")
    var postId: Long? = null,
    @Column(name = "detached_at")
    var detachedAt: OffsetDateTime? = null,
    @Column(name = "mime_type", length = 120)
    var mimeType: String? = null,
    @Column
    var width: Int? = null,
    @Column
    var height: Int? = null,
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,
    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
