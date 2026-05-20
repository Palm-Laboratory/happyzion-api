package org.happyzion.api.member.interfaces.dto

import jakarta.validation.constraints.*
import org.happyzion.api.member.application.*
import org.happyzion.api.member.domain.*
import java.time.LocalDate
import java.time.OffsetDateTime

data class ChurchMemberSaveRequest(
    @field:NotBlank @field:Size(max = 100) val name: String,
    @field:NotNull val sex: Sex,
    @field:NotNull val birthDate: LocalDate,
    @field:NotNull val birthCalendar: BirthCalendar,
    @field:NotBlank @field:Size(max = 30) val phone: String,
    @field:Size(max = 150) val email: String?,
    @field:NotBlank @field:Size(max = 200) val address: String,
    @field:Size(max = 200) val addressDetail: String?,
    @field:Size(max = 120) val job: String?,
    @field:Size(max = 120) val cellLabel: String?,
    @field:NotNull val status: ChurchMemberStatus,
    val faithStage: FaithStage?,
    @field:NotNull val office: ChurchMemberOffice,
    val officeAppointedAt: LocalDate?,
    @field:NotNull val registeredAt: LocalDate,
    val memo: String?,
    val faith: ChurchMemberFaithSaveRequest?,
) {
    fun toCommand() = ChurchMemberSaveCommand(
        name, phone, email, birthDate, birthCalendar, sex,
        address, addressDetail, job, cellLabel, status, faithStage,
        office, officeAppointedAt, registeredAt, memo, faith?.toCommand(),
    )
}

data class ChurchMemberFaithSaveRequest(
    val confessDate: LocalDate?, val learningDate: LocalDate?,
    val baptismDate: LocalDate?,
    @field:Size(max = 120) val baptismPlace: String?,
    @field:Size(max = 120) val baptismOfficiant: String?,
    val confirmationDate: LocalDate?,
    @field:Size(max = 120) val previousChurch: String?,
    val transferredInAt: LocalDate?,
) {
    fun toCommand() = ChurchMemberFaithSaveCommand(
        confessDate, learningDate, baptismDate, baptismPlace,
        baptismOfficiant, confirmationDate, previousChurch, transferredInAt,
    )
}

data class ChurchMemberPhotoAttachRequest(@field:NotNull val assetId: Long)

data class ChurchMemberSummaryResponse(
    val id: Long, val name: String, val phone: String,
    val status: ChurchMemberStatus, val cellLabel: String?, val registeredAt: LocalDate,
)
data class ChurchMemberPageResponse(val items: List<ChurchMemberSummaryResponse>, val hasNext: Boolean, val total: Long)

data class ChurchMemberDetailResponse(
    val id: Long, val name: String, val phone: String, val email: String?,
    val birthDate: LocalDate, val birthCalendar: BirthCalendar, val sex: Sex,
    val address: String, val addressDetail: String?, val job: String?,
    val memo: String?, val photoAssetId: Long?, val cellLabel: String?,
    val status: ChurchMemberStatus, val faithStage: FaithStage?,
    val office: ChurchMemberOffice, val officeAppointedAt: LocalDate?,
    val registeredAt: LocalDate,
    val faith: ChurchMemberFaithDetailResponse?,
    val createdAt: OffsetDateTime, val updatedAt: OffsetDateTime,
)
data class ChurchMemberFaithDetailResponse(
    val confessDate: LocalDate?, val learningDate: LocalDate?,
    val baptismDate: LocalDate?, val baptismPlace: String?, val baptismOfficiant: String?,
    val confirmationDate: LocalDate?, val previousChurch: String?, val transferredInAt: LocalDate?,
)
data class ChurchMemberAuditEntryResponse(
    val id: Long, val action: AuditAction, val actorId: Long,
    val diffJson: String?, val createdAt: OffsetDateTime,
)
data class ChurchMemberAuditPageResponse(val items: List<ChurchMemberAuditEntryResponse>, val hasNext: Boolean)
