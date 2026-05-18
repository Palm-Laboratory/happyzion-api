package org.happyzion.api.member.domain

import jakarta.persistence.*
import org.happyzion.api.common.security.pii.EncryptedStringConverter
import java.time.OffsetDateTime

@Entity
@Table(name = "church_member_audit_log")
class ChurchMemberAuditLog(
    @Column(name = "church_member_id", nullable = false) val churchMemberId: Long,
    @Column(name = "actor_id", nullable = false) val actorId: Long,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) val action: AuditAction,
    @Column(name = "diff_enc") @Convert(converter = EncryptedStringConverter::class)
    val diffJson: String?,
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        private set

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
}
