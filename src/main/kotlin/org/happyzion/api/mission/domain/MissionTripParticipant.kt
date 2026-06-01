package org.happyzion.api.mission.domain

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "mission_trip_participant")
class MissionTripParticipant(
    @Column(name = "mission_trip_id", nullable = false)
    val missionTripId: Long,

    @Column(name = "church_member_id")
    val churchMemberId: Long?,

    @Column(name = "external_name", length = 120)
    val externalName: String?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var role: ParticipantRole,

    @Enumerated(EnumType.STRING)
    @Column(name = "participation_status", nullable = false, length = 32)
    var participationStatus: ParticipationStatus,

    @Column(columnDefinition = "text")
    var note: String?,
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()

    init {
        require((churchMemberId != null) xor (externalName != null)) {
            "churchMemberId 또는 externalName 중 정확히 하나만 지정해야 합니다."
        }
    }
}
