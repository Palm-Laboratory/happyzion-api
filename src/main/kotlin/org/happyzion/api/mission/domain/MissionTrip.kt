package org.happyzion.api.mission.domain

import jakarta.persistence.*
import java.time.LocalDate
import java.time.OffsetDateTime

@Entity
@Table(name = "mission_trip")
class MissionTrip(
    @Column(nullable = false, length = 200)
    var title: String,

    @Column(nullable = false, length = 100)
    var country: String,

    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,

    @Column(name = "end_date")
    var endDate: LocalDate?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var type: MissionTripType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: MissionTripStatus,

    @Column(name = "leader_label", length = 120)
    var leaderLabel: String?,

    @Column
    var budget: Long?,

    @Column(columnDefinition = "text")
    var description: String?,

    @Column(name = "cover_photo_asset_id")
    var coverPhotoAssetId: Long?,
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
}
