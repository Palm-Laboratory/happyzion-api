package org.happyzion.api.mission.application

import org.happyzion.api.adminaccount.infrastructure.persistence.AdminAccountRepository
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.member.infrastructure.persistence.ChurchMemberRepository
import org.happyzion.api.mission.domain.MissionTrip
import org.happyzion.api.mission.domain.MissionTripParticipant
import org.happyzion.api.mission.domain.MissionTripStatus
import org.happyzion.api.mission.infrastructure.persistence.MissionTripParticipantRepository
import org.happyzion.api.mission.infrastructure.persistence.MissionTripRepository
import org.happyzion.api.mission.infrastructure.persistence.countByTripIds
import org.happyzion.api.mission.infrastructure.persistence.search
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class MissionTripService(
    private val tripRepository: MissionTripRepository,
    private val participantRepository: MissionTripParticipantRepository,
    private val memberRepository: ChurchMemberRepository,
    private val adminAccountRepository: AdminAccountRepository,
) {

    // ── Trip CRUD ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun listTrips(
        actorId: Long,
        year: Int?,
        status: MissionTripStatus?,
        country: String?,
        page: Int,
        size: Int,
    ): MissionTripPage {
        requireActiveAdmin(actorId)
        val trips = tripRepository.search(year, status, country)
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val totalElements = trips.size.toLong()
        val totalPages = if (trips.isEmpty()) 0 else ((trips.size + safeSize - 1) / safeSize)
        val pagedTrips = trips.drop(safePage * safeSize).take(safeSize)
        val tripIds = pagedTrips.mapNotNull { it.id }
        val countByTrip: Map<Long, Int> = if (tripIds.isEmpty()) emptyMap()
        else participantRepository.countByTripIds(tripIds)
        return MissionTripPage(
            trips = pagedTrips.map { it.toSummary(countByTrip[it.id!!] ?: 0) },
            page = safePage,
            size = safeSize,
            totalElements = totalElements,
            totalPages = totalPages,
        )
    }

    @Transactional(readOnly = true)
    fun getTrip(actorId: Long, tripId: Long): MissionTripDetail {
        requireActiveAdmin(actorId)
        val trip = requireTrip(tripId)
        val participants = participantRepository.findAllByMissionTripIdOrderByCreatedAtAscIdAsc(tripId)
        return trip.toDetail(participants.map { it.toDetail() })
    }

    @Transactional
    fun createTrip(actorId: Long, command: MissionTripCreateCommand): MissionTripDetail {
        requireActiveAdmin(actorId)
        val trip = tripRepository.save(
            MissionTrip(
                title = command.title,
                country = command.country,
                startDate = command.startDate,
                endDate = command.endDate,
                type = command.type,
                status = command.status,
                leaderLabel = command.leaderLabel,
                budget = command.budget,
                description = command.description,
                coverPhotoAssetId = null,
            )
        )
        return trip.toDetail(emptyList())
    }

    @Transactional
    fun updateTrip(actorId: Long, tripId: Long, command: MissionTripUpdateCommand): MissionTripDetail {
        requireActiveAdmin(actorId)
        val trip = requireTrip(tripId)
        trip.title = command.title
        trip.country = command.country
        trip.startDate = command.startDate
        trip.endDate = command.endDate
        trip.type = command.type
        trip.status = command.status
        trip.leaderLabel = command.leaderLabel
        trip.budget = command.budget
        trip.description = command.description
        trip.updatedAt = OffsetDateTime.now()
        tripRepository.save(trip)
        val participants = participantRepository.findAllByMissionTripIdOrderByCreatedAtAscIdAsc(tripId)
        return trip.toDetail(participants.map { it.toDetail() })
    }

    @Transactional
    fun deleteTrip(actorId: Long, tripId: Long) {
        requireActiveAdmin(actorId)
        val trip = requireTrip(tripId)
        tripRepository.delete(trip)
    }

    // ── Participants ──────────────────────────────────────────────────────────

    @Transactional
    fun addParticipant(actorId: Long, tripId: Long, command: AddParticipantCommand): MissionParticipantDetail {
        requireActiveAdmin(actorId)
        requireTrip(tripId)

        val externalName = command.externalName?.trim()?.takeIf { it.isNotEmpty() }
        if ((command.churchMemberId != null) == (externalName != null)) {
            throw IllegalArgumentException("교인 또는 외부인 이름 중 하나만 지정해 주세요.")
        }

        if (command.churchMemberId != null) {
            memberRepository.findByIdOrNull(command.churchMemberId)
                ?: throw NotFoundException("교인을 찾을 수 없습니다. id=${command.churchMemberId}")
            if (participantRepository.existsByMissionTripIdAndChurchMemberId(tripId, command.churchMemberId)) {
                throw IllegalArgumentException("이미 이 선교에 등록된 교인입니다.")
            }
        }

        val participant = participantRepository.save(
            MissionTripParticipant(
                missionTripId = tripId,
                churchMemberId = command.churchMemberId,
                externalName = externalName,
                role = command.role,
                participationStatus = command.participationStatus,
                note = command.note,
            )
        )
        return participant.toDetail()
    }

    @Transactional
    fun updateParticipant(
        actorId: Long,
        tripId: Long,
        participantId: Long,
        command: UpdateParticipantCommand,
    ): MissionParticipantDetail {
        requireActiveAdmin(actorId)
        val participant = requireParticipant(participantId, tripId)
        participant.role = command.role
        participant.participationStatus = command.participationStatus
        participant.note = command.note
        participant.updatedAt = OffsetDateTime.now()
        return participantRepository.save(participant).toDetail()
    }

    @Transactional
    fun removeParticipant(actorId: Long, tripId: Long, participantId: Long) {
        requireActiveAdmin(actorId)
        val participant = requireParticipant(participantId, tripId)
        participantRepository.delete(participant)
    }

    // ── Member's mission history ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun getMemberParticipations(actorId: Long, memberId: Long): List<MemberMissionParticipation> {
        requireActiveAdmin(actorId)
        memberRepository.findByIdOrNull(memberId)
            ?: throw NotFoundException("교인을 찾을 수 없습니다. id=$memberId")
        val participations = participantRepository.findAllByChurchMemberIdOrderByCreatedAtDescIdDesc(memberId)
        if (participations.isEmpty()) return emptyList()
        val tripIds = participations.map { it.missionTripId }.distinct()
        val tripMap = tripRepository.findAllById(tripIds).associateBy { it.id!! }
        return participations.mapNotNull { p ->
            val trip = tripMap[p.missionTripId] ?: return@mapNotNull null
            MemberMissionParticipation(
                participantId = p.id!!,
                tripId = trip.id!!,
                tripTitle = trip.title,
                country = trip.country,
                startDate = trip.startDate,
                endDate = trip.endDate,
                tripStatus = trip.status,
                role = p.role,
                participationStatus = p.participationStatus,
                note = p.note,
            )
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun requireTrip(tripId: Long): MissionTrip =
        tripRepository.findByIdOrNull(tripId)
            ?: throw NotFoundException("선교 여정을 찾을 수 없습니다. id=$tripId")

    private fun requireParticipant(participantId: Long, tripId: Long): MissionTripParticipant {
        val p = participantRepository.findByIdOrNull(participantId)
            ?: throw NotFoundException("참가자를 찾을 수 없습니다. id=$participantId")
        if (p.missionTripId != tripId) throw NotFoundException("참가자를 찾을 수 없습니다. id=$participantId")
        return p
    }

    private fun requireActiveAdmin(actorId: Long) {
        val actor = adminAccountRepository.findByIdOrNull(actorId)
            ?: throw NotFoundException("관리자 계정을 찾을 수 없습니다. id=$actorId")
        if (!actor.active) throw ForbiddenException("비활성화된 계정입니다.")
    }

    private fun MissionTrip.toSummary(participantCount: Int) = MissionTripSummary(
        id = id!!,
        title = title,
        country = country,
        startDate = startDate,
        endDate = endDate,
        type = type,
        status = status,
        leaderLabel = leaderLabel,
        participantCount = participantCount,
    )

    private fun MissionTrip.toDetail(participants: List<MissionParticipantDetail>) = MissionTripDetail(
        id = id!!,
        title = title,
        country = country,
        startDate = startDate,
        endDate = endDate,
        type = type,
        status = status,
        leaderLabel = leaderLabel,
        budget = budget,
        description = description,
        coverPhotoAssetId = coverPhotoAssetId,
        participants = participants,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun MissionTripParticipant.toDetail(): MissionParticipantDetail {
        val memberName = churchMemberId?.let {
            memberRepository.findByIdOrNull(it)?.name ?: "(알 수 없음)"
        }
        return MissionParticipantDetail(
            id = id!!,
            churchMemberId = churchMemberId,
            displayName = memberName ?: externalName ?: "(알 수 없음)",
            externalName = externalName,
            role = role,
            participationStatus = participationStatus,
            note = note,
            createdAt = createdAt,
        )
    }
}
