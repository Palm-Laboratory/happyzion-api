package org.happyzion.api.mission.infrastructure.persistence

import org.happyzion.api.mission.domain.MissionTrip
import org.happyzion.api.mission.domain.MissionTripStatus
import org.springframework.data.jpa.repository.JpaRepository

interface MissionTripRepository : JpaRepository<MissionTrip, Long> {

    fun findAllByOrderByStartDateDescIdDesc(): List<MissionTrip>
}

fun MissionTripRepository.search(year: Int?, status: MissionTripStatus?, country: String?): List<MissionTrip> =
    findAllByOrderByStartDateDescIdDesc().filter { trip ->
        (year == null || trip.startDate.year == year) &&
        (status == null || trip.status == status) &&
        (country == null || trip.country.contains(country, ignoreCase = true))
    }
