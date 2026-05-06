package org.happyzion.api.member.application

import com.fasterxml.jackson.databind.ObjectMapper
import org.happyzion.api.adminaccount.domain.AdminAccount
import org.happyzion.api.adminaccount.infrastructure.persistence.AdminAccountRepository
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.member.domain.AttendanceRecord
import org.happyzion.api.member.domain.AttendanceServiceDate
import org.happyzion.api.member.domain.Member
import org.happyzion.api.member.domain.MemberEventLog
import org.happyzion.api.member.domain.MemberEventType
import org.happyzion.api.member.domain.MemberFaith
import org.happyzion.api.member.domain.MemberStatus
import org.happyzion.api.member.domain.FaithStage
import org.happyzion.api.member.infrastructure.persistence.AttendanceRecordRepository
import org.happyzion.api.member.infrastructure.persistence.AttendanceServiceDateRepository
import org.happyzion.api.member.infrastructure.persistence.MemberEventLogRepository
import org.happyzion.api.member.infrastructure.persistence.MemberFaithRepository
import org.happyzion.api.member.infrastructure.persistence.MemberFamilyRepository
import org.happyzion.api.member.infrastructure.persistence.MemberRepository
import org.happyzion.api.member.infrastructure.persistence.MemberServiceRepository
import org.happyzion.api.member.infrastructure.persistence.MemberTagRepository
import org.happyzion.api.member.infrastructure.persistence.MemberTrainingRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime

@Service
class AdminMemberService(
    private val memberRepository: MemberRepository,
    private val memberFaithRepository: MemberFaithRepository,
    private val memberFamilyRepository: MemberFamilyRepository,
    private val memberServiceRepository: MemberServiceRepository,
    private val memberTrainingRepository: MemberTrainingRepository,
    private val memberTagRepository: MemberTagRepository,
    private val memberEventLogRepository: MemberEventLogRepository,
    private val attendanceServiceDateRepository: AttendanceServiceDateRepository,
    private val attendanceRecordRepository: AttendanceRecordRepository,
    private val adminAccountRepository: AdminAccountRepository,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional(readOnly = true)
    fun listMembers(
        actorId: Long,
        query: String?,
        status: MemberStatus?,
        stage: FaithStage?,
        cellId: String?,
        page: Int,
        size: Int,
    ): AdminMemberPage {
        requireActiveAdmin(actorId)

        val trimmedQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedCellId = cellId?.trim()?.takeIf { it.isNotEmpty() }
        val pageable = PageRequest.of(page, size)
        val result = if (trimmedQuery == null) {
            memberRepository.findAdminMembersWithoutQuery(
                status = status,
                stage = stage,
                cellId = normalizedCellId,
                pageable = pageable,
            )
        } else {
            val searchQuery = "%${trimmedQuery.lowercase()}%"
            val phoneQuery = "%$trimmedQuery%"
            memberRepository.findAdminMembersWithQuery(
                searchQuery = searchQuery,
                phoneQuery = phoneQuery,
                status = status,
                stage = stage,
                cellId = normalizedCellId,
                pageable = pageable,
            )
        }

        return AdminMemberPage(
            members = result.content.map { it.toSummary() },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            hasNext = result.hasNext(),
        )
    }

    @Transactional(readOnly = true)
    fun getMemberDetail(actorId: Long, memberId: Long): AdminMemberDetail {
        requireActiveAdmin(actorId)
        val member = requireMember(memberId)
        return toDetail(member)
    }

    @Transactional
    fun createMember(actorId: Long, command: CreateMemberCommand): AdminMemberDetail {
        requireActiveAdmin(actorId)
        validateCommand(command.name, command.phone, command.address)

        val saved = memberRepository.save(
            Member(
                name = command.name.trim(),
                nameEn = normalizeNullable(command.nameEn),
                baptismName = normalizeNullable(command.baptismName),
                sex = command.sex,
                birthDate = command.birthDate,
                birthCalendar = command.birthCalendar,
                phone = command.phone.trim(),
                emergencyPhone = normalizeNullable(command.emergencyPhone),
                emergencyRelation = normalizeNullable(command.emergencyRelation),
                email = normalizeNullable(command.email),
                address = command.address.trim(),
                addressDetail = normalizeNullable(command.addressDetail),
                job = normalizeNullable(command.job),
                photoPath = normalizeNullable(command.photoPath),
                cellId = normalizeNullable(command.cellId),
                cellLabel = normalizeNullable(command.cellLabel),
                status = command.status,
                faithStage = command.faithStage,
                office = command.office,
                officeAppointedAt = command.officeAppointedAt,
                registeredAt = command.registeredAt,
                memo = normalizeNullable(command.memo),
            )
        )

        val memberId = saved.id ?: throw IllegalStateException("저장된 교인 id가 없습니다.")
        upsertFaith(memberId, command.faith)
        appendEvent(
            memberId = memberId,
            actorId = actorId,
            type = MemberEventType.REGISTERED,
            payload = mapOf(
                "status" to saved.status.name,
                "faithStage" to saved.faithStage.name,
                "registeredAt" to saved.registeredAt.toString(),
            ),
        )

        return toDetail(requireMember(memberId))
    }

    @Transactional
    fun updateMember(actorId: Long, memberId: Long, command: UpdateMemberCommand): AdminMemberDetail {
        requireActiveAdmin(actorId)
        val member = requireMember(memberId)
        validateCommand(command.name, command.phone, command.address)

        val oldStatus = member.status
        val oldStage = member.faithStage
        val oldOffice = member.office
        val oldCellLabel = member.cellLabel
        val oldAddress = listOfNotNull(member.address, member.addressDetail).joinToString(" ")

        member.name = command.name.trim()
        member.nameEn = normalizeNullable(command.nameEn)
        member.baptismName = normalizeNullable(command.baptismName)
        member.sex = command.sex
        member.birthDate = command.birthDate
        member.birthCalendar = command.birthCalendar
        member.phone = command.phone.trim()
        member.emergencyPhone = normalizeNullable(command.emergencyPhone)
        member.emergencyRelation = normalizeNullable(command.emergencyRelation)
        member.email = normalizeNullable(command.email)
        member.address = command.address.trim()
        member.addressDetail = normalizeNullable(command.addressDetail)
        member.job = normalizeNullable(command.job)
        member.photoPath = normalizeNullable(command.photoPath)
        member.cellId = normalizeNullable(command.cellId)
        member.cellLabel = normalizeNullable(command.cellLabel)
        member.status = command.status
        member.faithStage = command.faithStage
        member.office = command.office
        member.officeAppointedAt = command.officeAppointedAt
        member.registeredAt = command.registeredAt
        member.memo = normalizeNullable(command.memo)

        memberRepository.save(member)
        upsertFaith(memberId, command.faith)

        if (oldStatus != member.status) {
            appendEvent(memberId, actorId, MemberEventType.STATUS_CHANGED, mapOf("before" to oldStatus.name, "after" to member.status.name))
        }
        if (oldStage != member.faithStage) {
            appendEvent(memberId, actorId, MemberEventType.STAGE_CHANGED, mapOf("before" to oldStage.name, "after" to member.faithStage.name))
        }
        if (oldOffice != member.office) {
            appendEvent(memberId, actorId, MemberEventType.OFFICE_CHANGED, mapOf("before" to oldOffice.name, "after" to member.office.name))
        }
        if (oldCellLabel != member.cellLabel) {
            appendEvent(memberId, actorId, MemberEventType.CELL_MOVED, mapOf("before" to oldCellLabel, "after" to member.cellLabel))
        }

        val newAddress = listOfNotNull(member.address, member.addressDetail).joinToString(" ")
        if (oldAddress != newAddress) {
            appendEvent(memberId, actorId, MemberEventType.ADDRESS_CHANGED, mapOf("before" to oldAddress, "after" to newAddress))
        }

        return toDetail(requireMember(memberId))
    }

    @Transactional(readOnly = true)
    fun getAttendance(
        actorId: Long,
        memberId: Long,
        from: LocalDate?,
        to: LocalDate?,
    ): List<AdminMemberAttendanceRecord> {
        requireActiveAdmin(actorId)
        requireMember(memberId)

        val serviceDates = attendanceServiceDateRepository.findAllInRange(from, to)
        val serviceDateIds = serviceDates.mapNotNull { it.id }
        if (serviceDateIds.isEmpty()) {
            return emptyList()
        }

        val recordsByServiceDateId = attendanceRecordRepository
            .findAllByMemberIdAndServiceDateIdIn(memberId, serviceDateIds)
            .associateBy { it.serviceDateId }

        return serviceDates.map { serviceDate ->
            val id = serviceDate.id ?: throw IllegalStateException("예배일 id가 없습니다.")
            val record = recordsByServiceDateId[id]
            AdminMemberAttendanceRecord(
                serviceDateId = id,
                serviceDate = serviceDate.serviceDate,
                serviceType = serviceDate.serviceType,
                status = record?.status,
                reason = record?.reason,
            )
        }
    }

    private fun toDetail(member: Member): AdminMemberDetail {
        val memberId = member.id ?: throw IllegalStateException("교인 id가 없습니다.")
        val faith = memberFaithRepository.findByIdOrNull(memberId)
        val family = memberFamilyRepository.findAllByMemberIdOrderByIsHeadDescIdAsc(memberId)
        val services = memberServiceRepository.findAllByMemberIdOrderByEndedAtAscStartedAtDescIdDesc(memberId)
        val trainings = memberTrainingRepository.findAllByMemberIdOrderByCompletedAtDescIdDesc(memberId)
        val tags = memberTagRepository.findAllByMemberIdOrderByTagAsc(memberId)
        val events = memberEventLogRepository.findAllByMemberIdOrderByCreatedAtDescIdDesc(memberId)
        val recentServiceDates = attendanceServiceDateRepository.findTop4ByOrderByServiceDateDescIdDesc()
        val recentAttendance = buildAttendanceWeeks(memberId, recentServiceDates)

        return AdminMemberDetail(
            id = memberId,
            name = member.name,
            nameEn = member.nameEn,
            baptismName = member.baptismName,
            sex = member.sex,
            birthDate = member.birthDate,
            birthCalendar = member.birthCalendar,
            phone = member.phone,
            emergencyPhone = member.emergencyPhone,
            emergencyRelation = member.emergencyRelation,
            email = member.email,
            address = member.address,
            addressDetail = member.addressDetail,
            job = member.job,
            photoPath = member.photoPath,
            cellId = member.cellId,
            cellLabel = member.cellLabel,
            status = member.status,
            faithStage = member.faithStage,
            office = member.office,
            officeAppointedAt = member.officeAppointedAt,
            registeredAt = member.registeredAt,
            memo = member.memo,
            createdAt = member.createdAt,
            updatedAt = member.updatedAt,
            faith = faith?.toModel(),
            family = family.map {
                AdminMemberFamilyItem(
                    id = it.id ?: throw IllegalStateException("가족관계 id가 없습니다."),
                    memberId = it.memberId,
                    relatedMemberId = it.relatedMemberId,
                    externalName = it.externalName,
                    relation = it.relation,
                    relationDetail = it.relationDetail,
                    isHead = it.isHead,
                    sex = it.sex,
                    phone = it.phone,
                    birthDate = it.birthDate,
                    groupNote = it.groupNote,
                )
            },
            services = services.map {
                AdminMemberServiceItem(
                    id = it.id ?: throw IllegalStateException("봉사 id가 없습니다."),
                    department = it.department,
                    team = it.team,
                    role = it.role,
                    startedAt = it.startedAt,
                    endedAt = it.endedAt,
                    schedule = it.schedule,
                    note = it.note,
                )
            },
            trainings = trainings.map {
                AdminMemberTrainingItem(
                    id = it.id ?: throw IllegalStateException("교육 id가 없습니다."),
                    programName = it.programName,
                    completedAt = it.completedAt,
                    note = it.note,
                )
            },
            tags = tags.map { it.tag },
            events = events.map {
                AdminMemberEventItem(
                    id = it.id ?: throw IllegalStateException("이력 id가 없습니다."),
                    type = it.type,
                    payload = it.payload,
                    actorId = it.actorId,
                    createdAt = it.createdAt,
                )
            },
            recentAttendance = recentAttendance,
        )
    }

    private fun Member.toSummary(): AdminMemberSummary =
        AdminMemberSummary(
            id = id ?: throw IllegalStateException("교인 id가 없습니다."),
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

    private fun MemberFaith.toModel(): AdminMemberFaithProfile =
        AdminMemberFaithProfile(
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

    private fun buildAttendanceWeeks(
        memberId: Long,
        serviceDates: List<AttendanceServiceDate>,
    ): List<AdminMemberAttendanceWeek> {
        val serviceDateIds = serviceDates.mapNotNull { it.id }
        if (serviceDateIds.isEmpty()) {
            return emptyList()
        }

        val recordsByServiceDateId = attendanceRecordRepository
            .findAllByMemberIdAndServiceDateIdIn(memberId, serviceDateIds)
            .associateBy { it.serviceDateId }

        return serviceDates.map { serviceDate ->
            val serviceDateId = serviceDate.id ?: throw IllegalStateException("예배일 id가 없습니다.")
            val record = recordsByServiceDateId[serviceDateId]
            AdminMemberAttendanceWeek(
                serviceDateId = serviceDateId,
                serviceDate = serviceDate.serviceDate,
                serviceType = serviceDate.serviceType,
                status = record?.status,
                reason = record?.reason,
            )
        }
    }

    private fun upsertFaith(memberId: Long, input: MemberFaithInput?) {
        if (input == null) return

        val current = memberFaithRepository.findByIdOrNull(memberId)
        val entity = if (current != null) {
            current.apply {
                confessDate = input.confessDate
                learningDate = input.learningDate
                baptismDate = input.baptismDate
                baptismPlace = normalizeNullable(input.baptismPlace)
                baptismOfficiant = normalizeNullable(input.baptismOfficiant)
                confirmationDate = input.confirmationDate
                previousChurch = normalizeNullable(input.previousChurch)
                transferredInAt = input.transferredInAt
            }
        } else {
            MemberFaith(
                memberId = memberId,
                confessDate = input.confessDate,
                learningDate = input.learningDate,
                baptismDate = input.baptismDate,
                baptismPlace = normalizeNullable(input.baptismPlace),
                baptismOfficiant = normalizeNullable(input.baptismOfficiant),
                confirmationDate = input.confirmationDate,
                previousChurch = normalizeNullable(input.previousChurch),
                transferredInAt = input.transferredInAt,
            )
        }

        memberFaithRepository.save(entity)
    }

    private fun appendEvent(
        memberId: Long,
        actorId: Long,
        type: MemberEventType,
        payload: Map<String, Any?>,
    ) {
        memberEventLogRepository.save(
            MemberEventLog(
                memberId = memberId,
                actorId = actorId,
                type = type,
                payload = objectMapper.writeValueAsString(payload),
                createdAt = OffsetDateTime.now(clock),
            )
        )
    }

    private fun requireMember(memberId: Long): Member =
        memberRepository.findByIdOrNull(memberId)
            ?: throw NotFoundException("교인을 찾을 수 없습니다. id=$memberId")

    private fun requireActiveAdmin(actorId: Long): AdminAccount {
        val actor = adminAccountRepository.findByIdOrNull(actorId)
            ?: throw NotFoundException("관리자 계정을 찾을 수 없습니다. id=$actorId")

        if (!actor.active) {
            throw ForbiddenException("비활성화된 계정은 교적부를 관리할 수 없습니다.")
        }

        return actor
    }

    private fun validateCommand(name: String, phone: String, address: String) {
        require(name.trim().isNotEmpty()) { "이름을 입력해 주세요." }
        require(phone.trim().isNotEmpty()) { "연락처를 입력해 주세요." }
        require(address.trim().isNotEmpty()) { "주소를 입력해 주세요." }
    }

    private fun normalizeNullable(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
}
