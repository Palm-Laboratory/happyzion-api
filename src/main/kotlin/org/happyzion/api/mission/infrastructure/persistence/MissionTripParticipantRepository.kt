package org.happyzion.api.mission.infrastructure.persistence

import org.happyzion.api.mission.domain.MissionTripParticipant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MissionTripParticipantRepository : JpaRepository<MissionTripParticipant, Long> {

    fun findAllByMissionTripIdOrderByCreatedAtAscIdAsc(missionTripId: Long): List<MissionTripParticipant>

    fun findAllByChurchMemberIdOrderByCreatedAtDescIdDesc(churchMemberId: Long): List<MissionTripParticipant>

    fun existsByMissionTripIdAndChurchMemberId(missionTripId: Long, churchMemberId: Long): Boolean

    @Query("select p.missionTripId as tripId, count(p) as cnt from MissionTripParticipant p where p.missionTripId in :tripIds group by p.missionTripId")
    fun countGroupByTripId(@Param("tripIds") tripIds: List<Long>): List<TripParticipantCount>

    interface TripParticipantCount {
        val tripId: Long
        val cnt: Long
    }
}

fun MissionTripParticipantRepository.countByTripIds(tripIds: List<Long>): Map<Long, Int> =
    countGroupByTripId(tripIds).associate { it.tripId to it.cnt.toInt() }
