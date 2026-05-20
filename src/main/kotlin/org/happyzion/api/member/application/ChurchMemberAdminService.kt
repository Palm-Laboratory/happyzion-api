package org.happyzion.api.member.application

import org.happyzion.api.adminaccount.application.AdminAccountGuard
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.common.security.pii.MemberSearchKeyNormalizer
import org.happyzion.api.common.security.pii.PiiHasher
import org.happyzion.api.member.domain.*
import org.happyzion.api.member.infrastructure.persistence.ChurchMemberAuditLogRepository
import org.happyzion.api.member.infrastructure.persistence.ChurchMemberFaithRepository
import org.happyzion.api.member.infrastructure.persistence.ChurchMemberRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ChurchMemberAdminService(
    private val memberRepo: ChurchMemberRepository,
    private val faithRepo: ChurchMemberFaithRepository,
    private val auditRepo: ChurchMemberAuditLogRepository,
    private val auditWriter: ChurchMemberAuditWriter,
    private val photoService: ChurchMemberPhotoService,
    private val adminAccountGuard: AdminAccountGuard,
    private val hasher: PiiHasher,
    private val normalizer: MemberSearchKeyNormalizer,
) {
    fun createMember(cmd: ChurchMemberSaveCommand, actorId: Long): ChurchMemberDetail {
        adminAccountGuard.verify(actorId)
        val member = ChurchMember.create(
            name = cmd.name, phone = cmd.phone, email = cmd.email,
            birthDate = cmd.birthDate, birthCalendar = cmd.birthCalendar, sex = cmd.sex,
            address = cmd.address, addressDetail = cmd.addressDetail, job = cmd.job,
            cellLabel = cmd.cellLabel, status = cmd.status, faithStage = cmd.faithStage,
            office = cmd.office, officeAppointedAt = cmd.officeAppointedAt,
            registeredAt = cmd.registeredAt, memo = cmd.memo,
            hasher = hasher, normalizer = normalizer,
        )
        val saved = memberRepo.save(member)
        val faith = cmd.faith?.let { f -> faithRepo.save(toFaithEntity(saved.id, f)) }
        auditWriter.recordCreate(saved.id, actorId, ChurchMemberSnapshot.of(saved))
        return toDetail(saved, faith)
    }

    fun updateMember(id: Long, cmd: ChurchMemberSaveCommand, actorId: Long): ChurchMemberDetail {
        adminAccountGuard.verify(actorId)
        val member = memberRepo.findById(id).orElseThrow { NotFoundException("교인을 찾을 수 없습니다.") }
        val before = ChurchMemberSnapshot.of(member)

        member.rename(cmd.name, hasher, normalizer)
        member.changePhone(cmd.phone, hasher, normalizer)
        member.changeEmail(cmd.email)
        member.changeBirth(cmd.birthDate, cmd.birthCalendar)
        member.changeSex(cmd.sex)
        member.changeAddress(cmd.address, cmd.addressDetail)
        member.changeJob(cmd.job)
        member.changeCellLabel(cmd.cellLabel)
        member.changeStatus(cmd.status)
        member.changeFaithStage(cmd.faithStage)
        member.appointOffice(cmd.office, cmd.officeAppointedAt)
        member.changeRegisteredAt(cmd.registeredAt)
        member.changeMemo(cmd.memo)

        val faith = upsertFaith(id, cmd.faith)
        val after = ChurchMemberSnapshot.of(member)
        auditWriter.recordUpdate(id, actorId, before, after)
        return toDetail(member, faith)
    }

    fun softDeleteMember(id: Long, actorId: Long) {
        adminAccountGuard.verify(actorId)
        val member = memberRepo.findById(id).orElseThrow { NotFoundException("교인을 찾을 수 없습니다.") }
        val before = ChurchMemberSnapshot.of(member)
        member.markRemoved()
        auditWriter.recordDelete(id, actorId, before)
    }

    fun attachPhoto(id: Long, assetId: Long, actorId: Long): ChurchMemberDetail {
        adminAccountGuard.verify(actorId)
        val member = memberRepo.findById(id).orElseThrow { NotFoundException("교인을 찾을 수 없습니다.") }
        val before = ChurchMemberSnapshot.of(member)
        photoService.replacePhoto(member, assetId, actorId)
        val after = ChurchMemberSnapshot.of(member)
        auditWriter.recordUpdate(id, actorId, before, after)
        return toDetail(member, faithRepo.findById(id).orElse(null))
    }

    fun detachPhoto(id: Long, actorId: Long): ChurchMemberDetail {
        adminAccountGuard.verify(actorId)
        val member = memberRepo.findById(id).orElseThrow { NotFoundException("교인을 찾을 수 없습니다.") }
        val before = ChurchMemberSnapshot.of(member)
        photoService.removePhoto(member)
        val after = ChurchMemberSnapshot.of(member)
        auditWriter.recordUpdate(id, actorId, before, after)
        return toDetail(member, faithRepo.findById(id).orElse(null))
    }

    @Transactional(readOnly = true)
    fun getMember(id: Long, actorId: Long): ChurchMemberDetail {
        adminAccountGuard.verify(actorId)
        val member = memberRepo.findById(id).orElseThrow { NotFoundException("교인을 찾을 수 없습니다.") }
        val faith = faithRepo.findById(id).orElse(null)
        return toDetail(member, faith)
    }

    @Transactional(readOnly = true)
    fun listAuditLogs(memberId: Long, actorId: Long, page: Int, size: Int): ChurchMemberAuditPage {
        adminAccountGuard.verify(actorId)
        val p = auditRepo.findByChurchMemberIdOrderByCreatedAtDescIdDesc(
            memberId, PageRequest.of(page, size)
        )
        return ChurchMemberAuditPage(
            items = p.content.map {
                ChurchMemberAuditEntry(it.id, it.action, it.actorId, it.diffJson, it.createdAt)
            },
            hasNext = p.hasNext(),
        )
    }

    @Transactional(readOnly = true)
    fun listMembers(
        filter: ChurchMemberSearchFilter, actorId: Long, page: Int, size: Int,
    ): ChurchMemberPage {
        adminAccountGuard.verify(actorId)
        val statuses = if (filter.includeInactive) ChurchMemberStatus.values().toSet() else ChurchMemberStatus.ACTIVE_SET

        val nameQuery = filter.name?.let(normalizer::forNameQuery)
        val phoneQuery = filter.phone?.let { normalizer.forPhoneQuery(it) }
        val phoneHash: String?
        val phoneLast4Hash: String?
        when (phoneQuery) {
            is org.happyzion.api.common.security.pii.PhoneQueryKey.Full -> {
                phoneHash = hasher.hash(phoneQuery.full); phoneLast4Hash = null
            }
            is org.happyzion.api.common.security.pii.PhoneQueryKey.Last4 -> {
                phoneHash = null; phoneLast4Hash = hasher.hash(phoneQuery.last4)
            }
            null -> { phoneHash = null; phoneLast4Hash = null }
        }

        if (filter.phone != null && phoneQuery == null) {
            return ChurchMemberPage(emptyList(), false, 0L)
        }

        // 이름 검색은 암호화 필드라 LIKE 불가 → 전체 로드 후 인메모리 필터링
        if (nameQuery != null) {
            val all = memberRepo.searchForNameFilter(
                phoneHash = phoneHash, phoneLast4Hash = phoneLast4Hash,
                faithStage = filter.faithStage, cellLabel = filter.cellLabel,
                statuses = statuses,
            ).filter { it.name.lowercase().contains(nameQuery) }
            val from = page * size
            val slice = all.drop(from).take(size)
            return ChurchMemberPage(
                items = slice.map { ChurchMemberSummary(it.id, it.name, it.phone, it.status, it.cellLabel, it.registeredAt) },
                hasNext = from + size < all.size,
                total = all.size.toLong(),
            )
        }

        val pageData = memberRepo.search(
            nameHash = null, phoneHash = phoneHash, phoneLast4Hash = phoneLast4Hash,
            faithStage = filter.faithStage, cellLabel = filter.cellLabel,
            statuses = statuses, pageable = PageRequest.of(page, size),
        )

        return ChurchMemberPage(
            items = pageData.content.map {
                ChurchMemberSummary(it.id, it.name, it.phone, it.status, it.cellLabel, it.registeredAt)
            },
            hasNext = pageData.hasNext(),
            total = pageData.totalElements,
        )
    }

    private fun upsertFaith(memberId: Long, cmd: ChurchMemberFaithSaveCommand?): ChurchMemberFaith? {
        if (cmd == null) {
            faithRepo.findById(memberId).ifPresent(faithRepo::delete)
            return null
        }
        val existing = faithRepo.findById(memberId).orElse(null)
        if (existing == null) return faithRepo.save(toFaithEntity(memberId, cmd))
        existing.confessDate = cmd.confessDate
        existing.learningDate = cmd.learningDate
        existing.baptismDate = cmd.baptismDate
        existing.baptismPlace = cmd.baptismPlace
        existing.baptismOfficiant = cmd.baptismOfficiant
        existing.confirmationDate = cmd.confirmationDate
        existing.previousChurch = cmd.previousChurch
        existing.transferredInAt = cmd.transferredInAt
        return faithRepo.save(existing)
    }

    private fun toFaithEntity(memberId: Long, cmd: ChurchMemberFaithSaveCommand) =
        ChurchMemberFaith(
            churchMemberId = memberId,
            confessDate = cmd.confessDate, learningDate = cmd.learningDate,
            baptismDate = cmd.baptismDate, baptismPlace = cmd.baptismPlace,
            baptismOfficiant = cmd.baptismOfficiant, confirmationDate = cmd.confirmationDate,
            previousChurch = cmd.previousChurch, transferredInAt = cmd.transferredInAt,
        )

    private fun toDetail(m: ChurchMember, f: ChurchMemberFaith?): ChurchMemberDetail =
        ChurchMemberDetail(
            id = m.id, name = m.name, phone = m.phone, email = m.email,
            birthDate = m.birthDate, birthCalendar = m.birthCalendar, sex = m.sex,
            address = m.address, addressDetail = m.addressDetail, job = m.job,
            memo = m.memo, photoAssetId = m.photoAssetId, cellLabel = m.cellLabel,
            status = m.status, faithStage = m.faithStage,
            office = m.office, officeAppointedAt = m.officeAppointedAt,
            registeredAt = m.registeredAt,
            faith = f?.let {
                ChurchMemberFaithDetail(
                    confessDate = it.confessDate, learningDate = it.learningDate,
                    baptismDate = it.baptismDate, baptismPlace = it.baptismPlace,
                    baptismOfficiant = it.baptismOfficiant, confirmationDate = it.confirmationDate,
                    previousChurch = it.previousChurch, transferredInAt = it.transferredInAt,
                )
            },
            createdAt = m.createdAt, updatedAt = m.updatedAt,
        )
}
