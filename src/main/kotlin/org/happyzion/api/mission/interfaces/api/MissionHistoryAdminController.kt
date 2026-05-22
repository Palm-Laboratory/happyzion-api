package org.happyzion.api.mission.interfaces.api

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.happyzion.api.common.security.AdminAuthRequired
import org.happyzion.api.mission.application.MissionEntryCommand
import org.happyzion.api.mission.application.MissionHistoryService
import org.happyzion.api.mission.application.MissionYearCreateCommand
import org.happyzion.api.mission.application.MissionYearDetail
import org.happyzion.api.mission.application.MissionYearSummary
import org.happyzion.api.mission.application.MissionYearUpdateCommand
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@AdminAuthRequired
@RestController
@RequestMapping("/api/v1/admin/mission-history")
class MissionHistoryAdminController(
    private val missionHistoryService: MissionHistoryService,
) {
    @GetMapping
    fun listYears(
        @RequestAttribute("adminAccountId") actorId: Long,
    ): MissionAdminListYearsResponse =
        MissionAdminListYearsResponse(years = missionHistoryService.listAdminYears(actorId).map { it.toResponse() })

    @GetMapping("/{yearId}")
    fun getYear(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable yearId: Long,
    ): MissionAdminYearDetailResponse =
        missionHistoryService.getAdminYear(actorId, yearId).toDetailResponse()

    @PostMapping
    fun createYear(
        @RequestAttribute("adminAccountId") actorId: Long,
        @Valid @RequestBody request: MissionYearCreateRequest,
    ): MissionAdminYearDetailResponse =
        missionHistoryService.createYear(actorId, request.toCommand()).toDetailResponse()

    @PutMapping("/{yearId}")
    fun updateYear(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable yearId: Long,
        @Valid @RequestBody request: MissionYearUpdateRequest,
    ): MissionAdminYearDetailResponse =
        missionHistoryService.updateYear(actorId, yearId, request.toCommand()).toDetailResponse()

    @PatchMapping("/reorder")
    fun reorderYears(
        @RequestAttribute("adminAccountId") actorId: Long,
        @RequestBody request: MissionYearReorderRequest,
    ) {
        missionHistoryService.reorderYears(actorId, request.yearIds)
    }

    @DeleteMapping("/{yearId}")
    fun deleteYear(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable yearId: Long,
    ) {
        missionHistoryService.deleteYear(actorId, yearId)
    }
}

data class MissionYearReorderRequest(
    val yearIds: List<Long>,
)

data class MissionYearCreateRequest(
    @field:NotBlank(message = "연도를 입력해 주세요.")
    @field:Size(max = 20, message = "연도는 20자 이내로 입력해 주세요.")
    val year: String,
    @field:NotBlank(message = "캡션을 입력해 주세요.")
    @field:Size(max = 200, message = "캡션은 200자 이내로 입력해 주세요.")
    val caption: String,
    @field:Pattern(regexp = "gold|red", message = "색상은 gold 또는 red만 사용할 수 있습니다.")
    val tone: String? = null,
    val sortOrder: Int? = null,
    @field:Valid
    val entries: List<MissionEntryRequest> = emptyList(),
) {
    fun toCommand() = MissionYearCreateCommand(
        year = year,
        caption = caption,
        tone = tone,
        sortOrder = sortOrder,
        entries = entries.map { it.toCommand() },
    )
}

data class MissionYearUpdateRequest(
    @field:NotBlank(message = "연도를 입력해 주세요.")
    @field:Size(max = 20, message = "연도는 20자 이내로 입력해 주세요.")
    val year: String,
    @field:NotBlank(message = "캡션을 입력해 주세요.")
    @field:Size(max = 200, message = "캡션은 200자 이내로 입력해 주세요.")
    val caption: String,
    @field:Pattern(regexp = "gold|red", message = "색상은 gold 또는 red만 사용할 수 있습니다.")
    val tone: String? = null,
    val sortOrder: Int? = null,
    @field:Valid
    val entries: List<MissionEntryRequest> = emptyList(),
) {
    fun toCommand() = MissionYearUpdateCommand(
        year = year,
        caption = caption,
        tone = tone,
        sortOrder = sortOrder,
        entries = entries.map { it.toCommand() },
    )
}

data class MissionEntryRequest(
    @field:Pattern(
        regexp = "Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec",
        message = "월은 Jan, Feb, Mar, Apr, May, Jun, Jul, Aug, Sep, Oct, Nov, Dec 중 하나여야 합니다.",
    )
    val month: String? = null,
    @field:NotBlank(message = "나라/지역명을 입력해 주세요.")
    @field:Size(max = 200, message = "나라/지역명은 200자 이내로 입력해 주세요.")
    val place: String,
    val isFirst: Boolean = false,
    val sortOrder: Int = 0,
) {
    fun toCommand() = MissionEntryCommand(
        month = month,
        place = place,
        isFirst = isFirst,
        sortOrder = sortOrder,
    )
}

data class MissionAdminListYearsResponse(
    val years: List<MissionAdminYearSummaryResponse>,
)

data class MissionAdminYearSummaryResponse(
    val id: Long,
    val year: String,
    val caption: String,
    val tone: String?,
    val sortOrder: Int,
    val entries: List<MissionAdminEntryResponse>,
)

data class MissionAdminYearDetailResponse(
    val id: Long,
    val year: String,
    val caption: String,
    val tone: String?,
    val sortOrder: Int,
    val entries: List<MissionAdminEntryResponse>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class MissionAdminEntryResponse(
    val id: Long,
    val month: String?,
    val place: String,
    val isFirst: Boolean,
    val sortOrder: Int,
)

private fun MissionYearSummary.toResponse() = MissionAdminYearSummaryResponse(
    id = id,
    year = year,
    caption = caption,
    tone = tone,
    sortOrder = sortOrder,
    entries = entries.map { it.toResponse() },
)

private fun MissionYearDetail.toDetailResponse() = MissionAdminYearDetailResponse(
    id = id,
    year = year,
    caption = caption,
    tone = tone,
    sortOrder = sortOrder,
    entries = entries.map { it.toResponse() },
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun org.happyzion.api.mission.application.MissionEntrySummary.toResponse() = MissionAdminEntryResponse(
    id = id,
    month = month,
    place = place,
    isFirst = isFirst,
    sortOrder = sortOrder,
)
