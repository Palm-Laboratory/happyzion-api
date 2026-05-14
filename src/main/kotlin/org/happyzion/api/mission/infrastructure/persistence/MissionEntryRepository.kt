package org.happyzion.api.mission.infrastructure.persistence

import org.happyzion.api.mission.domain.MissionEntry
import org.springframework.data.jpa.repository.JpaRepository

interface MissionEntryRepository : JpaRepository<MissionEntry, Long> {
    fun findAllByYearIdOrderBySortOrderAscIdAsc(yearId: Long): List<MissionEntry>
    fun findAllByYearIdInOrderBySortOrderAscIdAsc(yearIds: Collection<Long>): List<MissionEntry>
    fun deleteAllByYearId(yearId: Long)
}
