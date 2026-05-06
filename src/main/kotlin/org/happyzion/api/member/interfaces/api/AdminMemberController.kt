package org.happyzion.api.member.interfaces.api

import jakarta.validation.Valid
import org.happyzion.api.common.config.AdminProperties
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.member.application.AdminMemberAttendanceRecord
import org.happyzion.api.member.application.AdminMemberDetail
import org.happyzion.api.member.application.AdminMemberEventItem
import org.happyzion.api.member.application.AdminMemberFaithProfile
import org.happyzion.api.member.application.AdminMemberFamilyItem
import org.happyzion.api.member.application.AdminMemberPage
import org.happyzion.api.member.application.AdminMemberService
import org.happyzion.api.member.application.AdminMemberServiceItem
import org.happyzion.api.member.application.AdminMemberSummary
import org.happyzion.api.member.application.AdminMemberTrainingItem
import org.happyzion.api.member.application.AdminMemberAttendanceWeek
import org.happyzion.api.member.application.CreateMemberCommand
import org.happyzion.api.member.application.MemberFaithInput
import org.happyzion.api.member.application.UpdateMemberCommand
import org.happyzion.api.member.domain.BirthCalendar
import org.happyzion.api.member.domain.FaithStage
import org.happyzion.api.member.domain.FamilyRelation
import org.happyzion.api.member.domain.MemberEventType
import org.happyzion.api.member.domain.MemberSex
import org.happyzion.api.member.domain.MemberStatus
import org.happyzion.api.member.domain.Office
import org.happyzion.api.member.domain.AttendanceStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/admin/members")
class AdminMemberController(
    private val adminMemberService: AdminMemberService,
    private val adminProperties: AdminProperties,
) {
    @GetMapping
    fun listMembers(
        @RequestHeader("X-Admin-Key", required = false) adminKey: String?,
        @RequestHeader("X-Admin-Actor-Id") actorId: Long,
        @RequestParam(required = false) query: String? = null,
        @RequestParam(required = false) status: MemberStatus? = null,
        @RequestParam(required = false) stage: FaithStage? = null,
        @RequestParam(required = false) cellId: String? = null,
        @RequestParam(required = false, defaultValue = "0") page: Int = 0,
        @RequestParam(required = false, defaultValue = "20") size: Int = 20,
    ): AdminMembersPageResponse {
        validateAdminKey(adminKey)
        return adminMemberService
            .listMembers(actorId, query, status, stage, cellId, page, size)
            .toResponse()
    }

    @GetMapping("/{id}")
    fun getMember(
        @RequestHeader("X-Admin-Key", required = false) adminKey: String?,
        @RequestHeader("X-Admin-Actor-Id") actorId: Long,
        @PathVariable id: Long,
    ): AdminMemberDetailResponse {
        validateAdminKey(adminKey)
        return adminMemberService.getMemberDetail(actorId, id).toResponse()
    }

    @PostMapping
    fun createMember(
        @RequestHeader("X-Admin-Key", required = false) adminKey: String?,
        @RequestHeader("X-Admin-Actor-Id") actorId: Long,
        @Valid @RequestBody request: AdminMemberSaveRequest,
    ): AdminMemberDetailResponse {
        validateAdminKey(adminKey)
        return adminMemberService.createMember(actorId, request.toCreateCommand()).toResponse()
    }

    @PatchMapping("/{id}")
    fun updateMember(
        @RequestHeader("X-Admin-Key", required = false) adminKey: String?,
        @RequestHeader("X-Admin-Actor-Id") actorId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: AdminMemberSaveRequest,
    ): AdminMemberDetailResponse {
        validateAdminKey(adminKey)
        return adminMemberService.updateMember(actorId, id, request.toUpdateCommand()).toResponse()
    }

    @GetMapping("/{id}/attendance")
    fun getAttendance(
        @RequestHeader("X-Admin-Key", required = false) adminKey: String?,
        @RequestHeader("X-Admin-Actor-Id") actorId: Long,
        @PathVariable id: Long,
        @RequestParam(required = false) from: LocalDate? = null,
        @RequestParam(required = false) to: LocalDate? = null,
    ): AdminMemberAttendanceResponse {
        validateAdminKey(adminKey)
        return AdminMemberAttendanceResponse(
            records = adminMemberService.getAttendance(actorId, id, from, to).map { it.toResponse() },
        )
    }

    private fun validateAdminKey(adminKey: String?) {
        val configuredKey = adminProperties.syncKey.trim()
        if (configuredKey.isBlank()) {
            throw IllegalStateException("ADMIN_SYNC_KEY is not configured.")
        }

        if (adminKey.isNullOrBlank() || adminKey != configuredKey) {
            throw ForbiddenException("관리자 키가 올바르지 않습니다.")
        }
    }
}

data class AdminMemberSaveRequest(
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
    val faith: AdminMemberFaithRequest? = null,
) {
    fun toCreateCommand(): CreateMemberCommand =
        CreateMemberCommand(
            name = name,
            nameEn = nameEn,
            baptismName = baptismName,
            sex = sex,
            birthDate = birthDate,
            birthCalendar = birthCalendar,
            phone = phone,
            emergencyPhone = emergencyPhone,
            emergencyRelation = emergencyRelation,
            email = email,
            address = address,
            addressDetail = addressDetail,
            job = job,
            photoPath = photoPath,
            cellId = cellId,
            cellLabel = cellLabel,
            status = status,
            faithStage = faithStage,
            office = office,
            officeAppointedAt = officeAppointedAt,
            registeredAt = registeredAt,
            memo = memo,
            faith = faith?.toInput(),
        )

    fun toUpdateCommand(): UpdateMemberCommand =
        UpdateMemberCommand(
            name = name,
            nameEn = nameEn,
            baptismName = baptismName,
            sex = sex,
            birthDate = birthDate,
            birthCalendar = birthCalendar,
            phone = phone,
            emergencyPhone = emergencyPhone,
            emergencyRelation = emergencyRelation,
            email = email,
            address = address,
            addressDetail = addressDetail,
            job = job,
            photoPath = photoPath,
            cellId = cellId,
            cellLabel = cellLabel,
            status = status,
            faithStage = faithStage,
            office = office,
            officeAppointedAt = officeAppointedAt,
            registeredAt = registeredAt,
            memo = memo,
            faith = faith?.toInput(),
        )
}

data class AdminMemberFaithRequest(
    val confessDate: LocalDate? = null,
    val learningDate: LocalDate? = null,
    val baptismDate: LocalDate? = null,
    val baptismPlace: String? = null,
    val baptismOfficiant: String? = null,
    val confirmationDate: LocalDate? = null,
    val previousChurch: String? = null,
    val transferredInAt: LocalDate? = null,
) {
    fun toInput(): MemberFaithInput =
        MemberFaithInput(
            confessDate = confessDate,
            learningDate = learningDate,
            baptismDate = baptismDate,
            baptismPlace = baptismPlace,
            baptismOfficiant = baptismOfficiant,
            confirmationDate = confirmationDate,
            previousChurch = previousChurch,
            transferredInAt = transferredInAt,
        )
}

data class AdminMembersPageResponse(
    val members: List<AdminMemberSummaryResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val hasNext: Boolean,
)

data class AdminMemberSummaryResponse(
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

data class AdminMemberFaithResponse(
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

data class AdminMemberFamilyResponse(
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

data class AdminMemberServiceResponse(
    val id: Long,
    val department: String,
    val team: String?,
    val role: String,
    val startedAt: LocalDate,
    val endedAt: LocalDate?,
    val schedule: String?,
    val note: String?,
)

data class AdminMemberTrainingResponse(
    val id: Long,
    val programName: String,
    val completedAt: LocalDate,
    val note: String?,
)

data class AdminMemberEventResponse(
    val id: Long,
    val type: MemberEventType,
    val payload: String?,
    val actorId: Long,
    val createdAt: java.time.OffsetDateTime,
)

data class AdminMemberAttendanceWeekResponse(
    val serviceDateId: Long,
    val serviceDate: LocalDate,
    val serviceType: String,
    val status: AttendanceStatus?,
    val reason: String?,
)

data class AdminMemberDetailResponse(
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
    val createdAt: java.time.OffsetDateTime,
    val updatedAt: java.time.OffsetDateTime,
    val faith: AdminMemberFaithResponse?,
    val family: List<AdminMemberFamilyResponse>,
    val services: List<AdminMemberServiceResponse>,
    val trainings: List<AdminMemberTrainingResponse>,
    val tags: List<String>,
    val events: List<AdminMemberEventResponse>,
    val recentAttendance: List<AdminMemberAttendanceWeekResponse>,
)

data class AdminMemberAttendanceResponse(
    val records: List<AdminMemberAttendanceRecordResponse>,
)

data class AdminMemberAttendanceRecordResponse(
    val serviceDateId: Long,
    val serviceDate: LocalDate,
    val serviceType: String,
    val status: AttendanceStatus?,
    val reason: String?,
)

private fun AdminMemberPage.toResponse(): AdminMembersPageResponse =
    AdminMembersPageResponse(
        members = members.map { it.toResponse() },
        page = page,
        size = size,
        totalElements = totalElements,
        hasNext = hasNext,
    )

private fun AdminMemberSummary.toResponse(): AdminMemberSummaryResponse =
    AdminMemberSummaryResponse(
        id = id,
        name = name,
        nameEn = nameEn,
        baptismName = baptismName,
        sex = sex,
        birthDate = birthDate,
        birthCalendar = birthCalendar,
        phone = phone,
        address = address,
        addressDetail = addressDetail,
        cellId = cellId,
        cellLabel = cellLabel,
        status = status,
        faithStage = faithStage,
        office = office,
        registeredAt = registeredAt,
    )

private fun AdminMemberDetail.toResponse(): AdminMemberDetailResponse =
    AdminMemberDetailResponse(
        id = id,
        name = name,
        nameEn = nameEn,
        baptismName = baptismName,
        sex = sex,
        birthDate = birthDate,
        birthCalendar = birthCalendar,
        phone = phone,
        emergencyPhone = emergencyPhone,
        emergencyRelation = emergencyRelation,
        email = email,
        address = address,
        addressDetail = addressDetail,
        job = job,
        photoPath = photoPath,
        cellId = cellId,
        cellLabel = cellLabel,
        status = status,
        faithStage = faithStage,
        office = office,
        officeAppointedAt = officeAppointedAt,
        registeredAt = registeredAt,
        memo = memo,
        createdAt = createdAt,
        updatedAt = updatedAt,
        faith = faith?.toResponse(),
        family = family.map { it.toResponse() },
        services = services.map { it.toResponse() },
        trainings = trainings.map { it.toResponse() },
        tags = tags,
        events = events.map { it.toResponse() },
        recentAttendance = recentAttendance.map { it.toResponse() },
    )

private fun AdminMemberFaithProfile.toResponse(): AdminMemberFaithResponse =
    AdminMemberFaithResponse(
        memberId = memberId,
        confessDate = confessDate,
        learningDate = learningDate,
        baptismDate = baptismDate,
        baptismPlace = baptismPlace,
        baptismOfficiant = baptismOfficiant,
        confirmationDate = confirmationDate,
        previousChurch = previousChurch,
        transferredInAt = transferredInAt,
    )

private fun AdminMemberFamilyItem.toResponse(): AdminMemberFamilyResponse =
    AdminMemberFamilyResponse(
        id = id,
        memberId = memberId,
        relatedMemberId = relatedMemberId,
        externalName = externalName,
        relation = relation,
        relationDetail = relationDetail,
        isHead = isHead,
        sex = sex,
        phone = phone,
        birthDate = birthDate,
        groupNote = groupNote,
    )

private fun AdminMemberServiceItem.toResponse(): AdminMemberServiceResponse =
    AdminMemberServiceResponse(
        id = id,
        department = department,
        team = team,
        role = role,
        startedAt = startedAt,
        endedAt = endedAt,
        schedule = schedule,
        note = note,
    )

private fun AdminMemberTrainingItem.toResponse(): AdminMemberTrainingResponse =
    AdminMemberTrainingResponse(
        id = id,
        programName = programName,
        completedAt = completedAt,
        note = note,
    )

private fun AdminMemberEventItem.toResponse(): AdminMemberEventResponse =
    AdminMemberEventResponse(
        id = id,
        type = type,
        payload = payload,
        actorId = actorId,
        createdAt = createdAt,
    )

private fun AdminMemberAttendanceWeek.toResponse(): AdminMemberAttendanceWeekResponse =
    AdminMemberAttendanceWeekResponse(
        serviceDateId = serviceDateId,
        serviceDate = serviceDate,
        serviceType = serviceType,
        status = status,
        reason = reason,
    )

private fun AdminMemberAttendanceRecord.toResponse(): AdminMemberAttendanceRecordResponse =
    AdminMemberAttendanceRecordResponse(
        serviceDateId = serviceDateId,
        serviceDate = serviceDate,
        serviceType = serviceType,
        status = status,
        reason = reason,
    )
