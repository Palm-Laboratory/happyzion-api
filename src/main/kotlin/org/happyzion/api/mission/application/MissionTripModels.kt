package org.happyzion.api.mission.application

import org.happyzion.api.mission.domain.MissionTripStatus
import org.happyzion.api.mission.domain.MissionTripType
import org.happyzion.api.mission.domain.ParticipantRole
import org.happyzion.api.mission.domain.ParticipationStatus
import java.time.LocalDate
import java.time.OffsetDateTime

// ── Trip ─────────────────────────────────────────────────────────────────────

data class MissionTripSummary(
    val id: Long,
    val title: String,
    val country: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val type: MissionTripType,
    val status: MissionTripStatus,
    val leaderLabel: String?,
    val participantCount: Int,
)

data class MissionTripDetail(
    val id: Long,
    val title: String,
    val country: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val type: MissionTripType,
    val status: MissionTripStatus,
    val leaderLabel: String?,
    val budget: Long?,
    val description: String?,
    val coverPhotoAssetId: Long?,
    val participants: List<MissionParticipantDetail>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class MissionTripCreateCommand(
    val title: String,
    val country: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val type: MissionTripType,
    val status: MissionTripStatus,
    val leaderLabel: String?,
    val budget: Long?,
    val description: String?,
)

data class MissionTripUpdateCommand(
    val title: String,
    val country: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val type: MissionTripType,
    val status: MissionTripStatus,
    val leaderLabel: String?,
    val budget: Long?,
    val description: String?,
)

// ── Participant ───────────────────────────────────────────────────────────────

data class MissionParticipantDetail(
    val id: Long,
    val churchMemberId: Long?,
    val displayName: String,
    val externalName: String?,
    val role: ParticipantRole,
    val participationStatus: ParticipationStatus,
    val note: String?,
    val createdAt: OffsetDateTime,
)

data class AddParticipantCommand(
    val churchMemberId: Long?,
    val externalName: String?,
    val role: ParticipantRole,
    val participationStatus: ParticipationStatus,
    val note: String?,
)

data class UpdateParticipantCommand(
    val role: ParticipantRole,
    val participationStatus: ParticipationStatus,
    val note: String?,
)

// ── Member's mission history ──────────────────────────────────────────────────

data class MemberMissionParticipation(
    val participantId: Long,
    val tripId: Long,
    val tripTitle: String,
    val country: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val tripStatus: MissionTripStatus,
    val role: ParticipantRole,
    val participationStatus: ParticipationStatus,
    val note: String?,
)
