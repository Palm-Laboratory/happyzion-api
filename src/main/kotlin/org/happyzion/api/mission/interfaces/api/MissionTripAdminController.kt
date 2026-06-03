package org.happyzion.api.mission.interfaces.api

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.happyzion.api.common.security.AdminAuthRequired
import org.happyzion.api.mission.application.*
import org.happyzion.api.mission.domain.MissionTripStatus
import org.happyzion.api.mission.domain.MissionTripType
import org.happyzion.api.mission.domain.ParticipantRole
import org.happyzion.api.mission.domain.ParticipationStatus
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.OffsetDateTime

// ── Trip CRUD ─────────────────────────────────────────────────────────────────

@AdminAuthRequired
@RestController
@RequestMapping("/api/v1/admin/mission-trips")
class MissionTripAdminController(
    private val missionTripService: MissionTripService,
) {

    @GetMapping
    fun list(
        @RequestAttribute("adminAccountId") actorId: Long,
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) status: MissionTripStatus?,
        @RequestParam(required = false) country: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): MissionTripListResponse =
        missionTripService.listTrips(actorId, year, status, country, page, size).toResponse()

    @GetMapping("/{id}")
    fun get(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
    ): MissionTripDetailResponse =
        missionTripService.getTrip(actorId, id).toDetailResponse()

    @PostMapping
    fun create(
        @RequestAttribute("adminAccountId") actorId: Long,
        @Valid @RequestBody request: MissionTripSaveRequest,
    ): MissionTripDetailResponse =
        missionTripService.createTrip(actorId, request.toCreateCommand()).toDetailResponse()

    @PutMapping("/{id}")
    fun update(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: MissionTripSaveRequest,
    ): MissionTripDetailResponse =
        missionTripService.updateTrip(actorId, id, request.toUpdateCommand()).toDetailResponse()

    @DeleteMapping("/{id}")
    fun delete(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
    ) = missionTripService.deleteTrip(actorId, id)

    // ── Participants ──────────────────────────────────────────────────────────

    @PostMapping("/{id}/participants")
    fun addParticipant(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: AddParticipantRequest,
    ): ParticipantResponse =
        missionTripService.addParticipant(actorId, id, request.toCommand()).toResponse()

    @PatchMapping("/{id}/participants/{participantId}")
    fun updateParticipant(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
        @PathVariable participantId: Long,
        @Valid @RequestBody request: UpdateParticipantRequest,
    ): ParticipantResponse =
        missionTripService.updateParticipant(actorId, id, participantId, request.toCommand()).toResponse()

    @DeleteMapping("/{id}/participants/{participantId}")
    fun removeParticipant(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
        @PathVariable participantId: Long,
    ) = missionTripService.removeParticipant(actorId, id, participantId)
}

// ── Member's mission history (sub-resource under /admin/members) ──────────────

@AdminAuthRequired
@RestController
@RequestMapping("/api/v1/admin/members/{memberId}/missions")
class MemberMissionsController(
    private val missionTripService: MissionTripService,
) {
    @GetMapping
    fun getMemberMissions(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable memberId: Long,
    ): MemberMissionListResponse =
        MemberMissionListResponse(
            missionTripService.getMemberParticipations(actorId, memberId).map { it.toResponse() }
        )
}

// ── Request DTOs ──────────────────────────────────────────────────────────────

data class MissionTripSaveRequest(
    @field:NotBlank(message = "제목을 입력해 주세요.")
    @field:Size(max = 200, message = "제목은 200자 이내로 입력해 주세요.")
    val title: String,

    @field:NotBlank(message = "나라/지역을 입력해 주세요.")
    @field:Size(max = 100, message = "나라/지역은 100자 이내로 입력해 주세요.")
    val country: String,

    @field:NotNull(message = "출발일을 입력해 주세요.")
    val startDate: LocalDate,

    val endDate: LocalDate?,

    @field:NotNull(message = "선교 유형을 선택해 주세요.")
    val type: MissionTripType,

    @field:NotNull(message = "상태를 선택해 주세요.")
    val status: MissionTripStatus,

    @field:Size(max = 120, message = "인솔자는 120자 이내로 입력해 주세요.")
    val leaderLabel: String?,

    val budget: Long?,

    val description: String?,
) {
    fun toCreateCommand() = MissionTripCreateCommand(
        title = title, country = country, startDate = startDate, endDate = endDate,
        type = type, status = status, leaderLabel = leaderLabel,
        budget = budget, description = description,
    )

    fun toUpdateCommand() = MissionTripUpdateCommand(
        title = title, country = country, startDate = startDate, endDate = endDate,
        type = type, status = status, leaderLabel = leaderLabel,
        budget = budget, description = description,
    )
}

data class AddParticipantRequest(
    val churchMemberId: Long?,
    @field:Size(max = 120, message = "이름은 120자 이내로 입력해 주세요.")
    val externalName: String?,
    @field:NotNull(message = "역할을 선택해 주세요.")
    val role: ParticipantRole,
    @field:NotNull(message = "참가 상태를 선택해 주세요.")
    val participationStatus: ParticipationStatus,
    val note: String?,
) {
    fun toCommand() = AddParticipantCommand(
        churchMemberId = churchMemberId,
        externalName = externalName,
        role = role,
        participationStatus = participationStatus,
        note = note,
    )
}

data class UpdateParticipantRequest(
    @field:NotNull(message = "역할을 선택해 주세요.")
    val role: ParticipantRole,
    @field:NotNull(message = "참가 상태를 선택해 주세요.")
    val participationStatus: ParticipationStatus,
    val note: String?,
) {
    fun toCommand() = UpdateParticipantCommand(
        role = role,
        participationStatus = participationStatus,
        note = note,
    )
}

// ── Response DTOs ─────────────────────────────────────────────────────────────

data class MissionTripListResponse(
    val trips: List<MissionTripSummaryResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class MissionTripSummaryResponse(
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

data class MissionTripDetailResponse(
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
    val participants: List<ParticipantResponse>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class ParticipantResponse(
    val id: Long,
    val churchMemberId: Long?,
    val displayName: String,
    val externalName: String?,
    val role: ParticipantRole,
    val participationStatus: ParticipationStatus,
    val note: String?,
    val createdAt: OffsetDateTime,
)

data class MemberMissionListResponse(val participations: List<MemberMissionParticipationResponse>)

data class MemberMissionParticipationResponse(
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

// ── Mappers ───────────────────────────────────────────────────────────────────

private fun MissionTripSummary.toResponse() = MissionTripSummaryResponse(
    id = id, title = title, country = country,
    startDate = startDate, endDate = endDate,
    type = type, status = status, leaderLabel = leaderLabel,
    participantCount = participantCount,
)

private fun MissionTripPage.toResponse() = MissionTripListResponse(
    trips = trips.map { it.toResponse() },
    page = page,
    size = size,
    totalElements = totalElements,
    totalPages = totalPages,
)

private fun MissionTripDetail.toDetailResponse() = MissionTripDetailResponse(
    id = id, title = title, country = country,
    startDate = startDate, endDate = endDate,
    type = type, status = status, leaderLabel = leaderLabel,
    budget = budget, description = description,
    coverPhotoAssetId = coverPhotoAssetId,
    participants = participants.map { it.toResponse() },
    createdAt = createdAt, updatedAt = updatedAt,
)

private fun MissionParticipantDetail.toResponse() = ParticipantResponse(
    id = id, churchMemberId = churchMemberId,
    displayName = displayName, externalName = externalName,
    role = role, participationStatus = participationStatus,
    note = note, createdAt = createdAt,
)

private fun MemberMissionParticipation.toResponse() = MemberMissionParticipationResponse(
    participantId = participantId, tripId = tripId,
    tripTitle = tripTitle, country = country,
    startDate = startDate, endDate = endDate,
    tripStatus = tripStatus, role = role,
    participationStatus = participationStatus, note = note,
)
