package org.happyzion.api.mission.infrastructure.persistence

import org.happyzion.api.mission.domain.MissionYear
import org.springframework.data.jpa.repository.JpaRepository

interface MissionYearRepository : JpaRepository<MissionYear, Long> {
    fun findAllByOrderBySortOrderAscIdAsc(): List<MissionYear>
}
