package org.happyzion.api.mission.interfaces.api

import org.happyzion.api.mission.application.MissionHistoryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/public/mission-history")
class PublicMissionHistoryController(
    private val missionHistoryService: MissionHistoryService,
) {
    @GetMapping
    fun listYears(): MissionPublicListYearsResponse {
        return MissionPublicListYearsResponse(
            years = missionHistoryService.listYears().map { year ->
                MissionPublicYearResponse(
                    id = year.id,
                    year = year.year,
                    caption = year.caption,
                    tone = year.tone,
                    entries = year.entries.map { entry ->
                        MissionPublicEntryResponse(
                            id = entry.id,
                            month = entry.month,
                            place = entry.place,
                            isFirst = entry.isFirst,
                        )
                    },
                )
            }
        )
    }
}

data class MissionPublicListYearsResponse(
    val years: List<MissionPublicYearResponse>,
)

data class MissionPublicYearResponse(
    val id: Long,
    val year: String,
    val caption: String,
    val tone: String?,
    val entries: List<MissionPublicEntryResponse>,
)

data class MissionPublicEntryResponse(
    val id: Long,
    val month: String?,
    val place: String,
    val isFirst: Boolean,
)
