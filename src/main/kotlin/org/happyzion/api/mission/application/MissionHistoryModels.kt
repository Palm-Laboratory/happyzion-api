package org.happyzion.api.mission.application

import java.time.OffsetDateTime

data class MissionYearSummary(
    val id: Long,
    val year: String,
    val caption: String,
    val tone: String?,
    val sortOrder: Int,
    val entries: List<MissionEntrySummary>,
)

data class MissionEntrySummary(
    val id: Long,
    val month: String?,
    val place: String,
    val isFirst: Boolean,
    val sortOrder: Int,
)

data class MissionYearDetail(
    val id: Long,
    val year: String,
    val caption: String,
    val tone: String?,
    val sortOrder: Int,
    val entries: List<MissionEntrySummary>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class MissionYearCreateCommand(
    val year: String,
    val caption: String,
    val tone: String?,
    val sortOrder: Int? = null,
    val entries: List<MissionEntryCommand>,
)

data class MissionYearUpdateCommand(
    val year: String,
    val caption: String,
    val tone: String?,
    val sortOrder: Int? = null,
    val entries: List<MissionEntryCommand>,
)

data class MissionYearBatchUpdateCommand(
    val yearId: Long,
    val command: MissionYearUpdateCommand,
)

data class MissionEntryCommand(
    val month: String?,
    val place: String,
    val isFirst: Boolean,
    val sortOrder: Int = 0,
)
