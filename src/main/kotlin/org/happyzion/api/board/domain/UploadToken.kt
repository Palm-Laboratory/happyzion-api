package org.happyzion.api.board.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(name = "upload_token")
class UploadToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "actor_id", nullable = false)
    var actorId: Long,
    @Column(name = "max_byte_size", nullable = false)
    var maxByteSize: Long,
    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    var tokenHash: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_kind", nullable = false, length = 32)
    var assetKind: PostAssetKind,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_mime_types", nullable = false, columnDefinition = "jsonb")
    var allowedMimeTypes: String = "[]",
    @Column(name = "expires_at", nullable = false)
    var expiresAt: OffsetDateTime,
    @Column(name = "used_at")
    var usedAt: OffsetDateTime? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
