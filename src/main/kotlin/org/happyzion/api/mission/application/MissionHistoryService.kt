package org.happyzion.api.mission.application

import org.happyzion.api.adminaccount.infrastructure.persistence.AdminAccountRepository
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.mission.domain.MissionEntry
import org.happyzion.api.mission.domain.MissionYear
import org.happyzion.api.mission.infrastructure.persistence.MissionEntryRepository
import org.happyzion.api.mission.infrastructure.persistence.MissionYearRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class MissionHistoryService(
    private val missionYearRepository: MissionYearRepository,
    private val missionEntryRepository: MissionEntryRepository,
    private val adminAccountRepository: AdminAccountRepository,
) {

    @Transactional(readOnly = true)
    fun listYears(): List<MissionYearSummary> {
        val years = missionYearRepository.findAllByOrderBySortOrderAscIdAsc()
        val yearIds = years.mapNotNull { it.id }
        val entriesByYearId: Map<Long, List<MissionEntry>> = if (yearIds.isEmpty()) emptyMap()
        else missionEntryRepository.findAllByYearIdInOrderBySortOrderAscIdAsc(yearIds)
            .groupBy { entry -> entry.yearId }

        return years.map { year ->
            val entries = entriesByYearId[year.id] ?: emptyList()
            year.toSummary(entries.map { it.toSummary() })
        }
    }

    @Transactional(readOnly = true)
    fun getYear(yearId: Long): MissionYearDetail {
        val year = requireYear(yearId)
        val entries = missionEntryRepository.findAllByYearIdOrderBySortOrderAscIdAsc(yearId)
        return year.toDetail(entries.map { it.toSummary() })
    }

    @Transactional
    fun createYear(actorId: Long, command: MissionYearCreateCommand): MissionYearDetail {
        requireActiveAdmin(actorId)
        val maxSortOrder = missionYearRepository.findAllByOrderBySortOrderAscIdAsc()
            .maxOfOrNull { it.sortOrder } ?: -1
        val year = missionYearRepository.save(
            MissionYear(
                year = command.year,
                caption = command.caption,
                tone = command.tone,
                sortOrder = command.sortOrder ?: (maxSortOrder + 1),
            )
        )
        val yearId = year.id ?: throw IllegalStateException("저장된 연도 id가 없습니다.")
        val entries = saveEntries(yearId, command.entries)
        return year.toDetail(entries.map { it.toSummary() })
    }

    @Transactional
    fun updateYear(actorId: Long, yearId: Long, command: MissionYearUpdateCommand): MissionYearDetail {
        requireActiveAdmin(actorId)
        val year = requireYear(yearId)
        year.year = command.year
        year.caption = command.caption
        year.tone = command.tone
        command.sortOrder?.let { year.sortOrder = it }
        year.updatedAt = OffsetDateTime.now()
        missionYearRepository.save(year)

        missionEntryRepository.deleteAllByYearId(yearId)
        val entries = saveEntries(yearId, command.entries)
        return year.toDetail(entries.map { it.toSummary() })
    }

    @Transactional
    fun deleteYear(actorId: Long, yearId: Long) {
        requireActiveAdmin(actorId)
        val year = requireYear(yearId)
        missionEntryRepository.deleteAllByYearId(yearId)
        missionYearRepository.delete(year)
    }

    private fun saveEntries(yearId: Long, commands: List<MissionEntryCommand>): List<MissionEntry> {
        if (commands.isEmpty()) return emptyList()
        return missionEntryRepository.saveAll(
            commands.mapIndexed { index, cmd ->
                MissionEntry(
                    yearId = yearId,
                    month = cmd.month,
                    place = cmd.place,
                    isFirst = cmd.isFirst,
                    sortOrder = index,
                )
            }
        )
    }

    private fun requireYear(yearId: Long): MissionYear =
        missionYearRepository.findByIdOrNull(yearId)
            ?: throw NotFoundException("선교 이력을 찾을 수 없습니다. id=$yearId")

    private fun requireActiveAdmin(actorId: Long) {
        val actor = adminAccountRepository.findByIdOrNull(actorId)
            ?: throw NotFoundException("관리자 계정을 찾을 수 없습니다. id=$actorId")
        if (!actor.active) throw ForbiddenException("비활성화된 계정입니다.")
    }

    private fun MissionYear.toSummary(entries: List<MissionEntrySummary>) = MissionYearSummary(
        id = id!!,
        year = year,
        caption = caption,
        tone = tone,
        sortOrder = sortOrder,
        entries = entries,
    )

    private fun MissionYear.toDetail(entries: List<MissionEntrySummary>) = MissionYearDetail(
        id = id!!,
        year = year,
        caption = caption,
        tone = tone,
        sortOrder = sortOrder,
        entries = entries,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun MissionEntry.toSummary() = MissionEntrySummary(
        id = id!!,
        month = month,
        place = place,
        isFirst = isFirst,
        sortOrder = sortOrder,
    )
}
