package org.happyzion.api.member.application

import org.happyzion.api.member.domain.AttendanceStatus
import org.happyzion.api.member.domain.BirthCalendar
import org.happyzion.api.member.domain.FaithStage
import org.happyzion.api.member.domain.FamilyRelation
import org.happyzion.api.member.domain.MemberEventType
import org.happyzion.api.member.domain.MemberSex
import org.happyzion.api.member.domain.MemberStatus
import org.happyzion.api.member.domain.Office
import java.time.LocalDate
import java.time.OffsetDateTime

data class CreateMemberCommand(
    val name: String,
    val nameEn: String? = null,
    val baptismName: String? = null,
    val sex: MemberSex,
    val birthDate: LocalDate,
    val birthCalendar: BirthCalendar,
    val phone: String,
    val emergencyPhone: String? = null,
    val emergencyRelation: String? = null,
    val email: String? = null,
    val address: String,
    val addressDetail: String? = null,
    val job: String? = null,
    val photoPath: String? = null,
    val cellId: String? = null,
    val cellLabel: String? = null,
    val status: MemberStatus,
    val faithStage: FaithStage,
    val office: Office = Office.LAY,
    val officeAppointedAt: LocalDate? = null,
    val registeredAt: LocalDate,
    val memo: String? = null,
    val faith: MemberFaithInput? = null,
)

data class UpdateMemberCommand(
    val name: String,
    val nameEn: String? = null,
    val baptismName: String? = null,
    val sex: MemberSex,
    val birthDate: LocalDate,
    val birthCalendar: BirthCalendar,
    val phone: String,
    val emergencyPhone: String? = null,
    val emergencyRelation: String? = null,
    val email: String? = null,
    val address: String,
    val addressDetail: String? = null,
    val job: String? = null,
    val photoPath: String? = null,
    val cellId: String? = null,
    val cellLabel: String? = null,
    val status: MemberStatus,
    val faithStage: FaithStage,
    val office: Office = Office.LAY,
    val officeAppointedAt: LocalDate? = null,
    val registeredAt: LocalDate,
    val memo: String? = null,
    val faith: MemberFaithInput? = null,
)

data class MemberFaithInput(
    val confessDate: LocalDate? = null,
    val learningDate: LocalDate? = null,
    val baptismDate: LocalDate? = null,
    val baptismPlace: String? = null,
    val baptismOfficiant: String? = null,
    val confirmationDate: LocalDate? = null,
    val previousChurch: String? = null,
    val transferredInAt: LocalDate? = null,
)

data class AdminMemberSummary(
    val id: Long,
    val name: String,
    val nameEn: String?,
    val baptismName: String?,
    val sex: MemberSex,
    val birthDate: LocalDate,
    val birthCalendar: BirthCalendar,
    val phone: String,
    val address: String,
    val addressDetail: String?,
    val cellId: String?,
    val cellLabel: String?,
    val status: MemberStatus,
    val faithStage: FaithStage,
    val office: Office,
    val registeredAt: LocalDate,
)

data class AdminMemberPage(
    val members: List<AdminMemberSummary>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val hasNext: Boolean,
)

data class AdminMemberFaithProfile(
    val memberId: Long,
    val confessDate: LocalDate?,
    val learningDate: LocalDate?,
    val baptismDate: LocalDate?,
    val baptismPlace: String?,
    val baptismOfficiant: String?,
    val confirmationDate: LocalDate?,
    val previousChurch: String?,
    val transferredInAt: LocalDate?,
)

data class AdminMemberFamilyItem(
    val id: Long,
    val memberId: Long,
    val relatedMemberId: Long?,
    val externalName: String?,
    val relation: FamilyRelation,
    val relationDetail: String?,
    val isHead: Boolean,
    val sex: MemberSex?,
    val phone: String?,
    val birthDate: LocalDate?,
    val groupNote: String?,
)

data class AdminMemberServiceItem(
    val id: Long,
    val department: String,
    val team: String?,
    val role: String,
    val startedAt: LocalDate,
    val endedAt: LocalDate?,
    val schedule: String?,
    val note: String?,
)

data class AdminMemberTrainingItem(
    val id: Long,
    val programName: String,
    val completedAt: LocalDate,
    val note: String?,
)

data class AdminMemberEventItem(
    val id: Long,
    val type: MemberEventType,
    val payload: String?,
    val actorId: Long,
    val createdAt: OffsetDateTime,
)

data class AdminMemberAttendanceWeek(
    val serviceDateId: Long,
    val serviceDate: LocalDate,
    val serviceType: String,
    val status: AttendanceStatus?,
    val reason: String?,
)

data class AdminMemberDetail(
    val id: Long,
    val name: String,
    val nameEn: String?,
    val baptismName: String?,
    val sex: MemberSex,
    val birthDate: LocalDate,
    val birthCalendar: BirthCalendar,
    val phone: String,
    val emergencyPhone: String?,
    val emergencyRelation: String?,
    val email: String?,
    val address: String,
    val addressDetail: String?,
    val job: String?,
    val photoPath: String?,
    val cellId: String?,
    val cellLabel: String?,
    val status: MemberStatus,
    val faithStage: FaithStage,
    val office: Office,
    val officeAppointedAt: LocalDate?,
    val registeredAt: LocalDate,
    val memo: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val faith: AdminMemberFaithProfile?,
    val family: List<AdminMemberFamilyItem>,
    val services: List<AdminMemberServiceItem>,
    val trainings: List<AdminMemberTrainingItem>,
    val tags: List<String>,
    val events: List<AdminMemberEventItem>,
    val recentAttendance: List<AdminMemberAttendanceWeek>,
)

data class AdminMemberAttendanceRecord(
    val serviceDateId: Long,
    val serviceDate: LocalDate,
    val serviceType: String,
    val status: AttendanceStatus?,
    val reason: String?,
)
