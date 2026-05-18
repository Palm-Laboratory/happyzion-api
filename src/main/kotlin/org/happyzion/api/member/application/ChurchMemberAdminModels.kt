package org.happyzion.api.member.application

import org.happyzion.api.member.domain.*
import java.time.LocalDate
import java.time.OffsetDateTime

data class ChurchMemberSaveCommand(
    val name: String, val phone: String, val email: String?,
    val birthDate: LocalDate, val birthCalendar: BirthCalendar, val sex: Sex,
    val address: String, val addressDetail: String?, val job: String?,
    val cellLabel: String?, val status: ChurchMemberStatus, val faithStage: FaithStage?,
    val office: ChurchMemberOffice, val officeAppointedAt: LocalDate?,
    val registeredAt: LocalDate, val memo: String?,
    val faith: ChurchMemberFaithSaveCommand?,
)

data class ChurchMemberFaithSaveCommand(
    val confessDate: LocalDate?, val learningDate: LocalDate?,
    val baptismDate: LocalDate?, val baptismPlace: String?, val baptismOfficiant: String?,
    val confirmationDate: LocalDate?, val previousChurch: String?, val transferredInAt: LocalDate?,
)

data class ChurchMemberSearchFilter(
    val name: String?, val phone: String?,
    val statuses: Set<ChurchMemberStatus>,
    val faithStage: FaithStage?, val cellLabel: String?,
    val includeInactive: Boolean,
)

data class ChurchMemberSummary(
    val id: Long, val name: String, val phone: String, val status: ChurchMemberStatus,
    val cellLabel: String?, val registeredAt: LocalDate,
)

data class ChurchMemberPage(val items: List<ChurchMemberSummary>, val hasNext: Boolean)

data class ChurchMemberDetail(
    val id: Long, val name: String, val phone: String, val email: String?,
    val birthDate: LocalDate, val birthCalendar: BirthCalendar, val sex: Sex,
    val address: String, val addressDetail: String?, val job: String?,
    val memo: String?, val photoAssetId: Long?, val cellLabel: String?,
    val status: ChurchMemberStatus, val faithStage: FaithStage?,
    val office: ChurchMemberOffice, val officeAppointedAt: LocalDate?,
    val registeredAt: LocalDate,
    val faith: ChurchMemberFaithDetail?,
    val createdAt: OffsetDateTime, val updatedAt: OffsetDateTime,
)

data class ChurchMemberFaithDetail(
    val confessDate: LocalDate?, val learningDate: LocalDate?,
    val baptismDate: LocalDate?, val baptismPlace: String?, val baptismOfficiant: String?,
    val confirmationDate: LocalDate?, val previousChurch: String?, val transferredInAt: LocalDate?,
)

data class ChurchMemberAuditEntry(
    val id: Long, val action: AuditAction, val actorId: Long,
    val diffJson: String?, val createdAt: OffsetDateTime,
)

data class ChurchMemberAuditPage(val items: List<ChurchMemberAuditEntry>, val hasNext: Boolean)
