package org.happyzion.api.member.application

import com.fasterxml.jackson.databind.ObjectMapper
import org.happyzion.api.common.security.pii.PiiEncryptor
import org.happyzion.api.member.domain.AuditAction
import org.happyzion.api.member.domain.ChurchMemberAuditLog
import org.happyzion.api.member.infrastructure.persistence.ChurchMemberAuditLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChurchMemberAuditWriter(
    private val auditLogRepository: ChurchMemberAuditLogRepository,
    private val encryptor: PiiEncryptor,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun recordCreate(memberId: Long, actorId: Long, after: ChurchMemberSnapshot) {
        save(memberId, actorId, AuditAction.CREATE, fieldMap(after))
    }

    @Transactional
    fun recordUpdate(memberId: Long, actorId: Long, before: ChurchMemberSnapshot, after: ChurchMemberSnapshot) {
        val diff = computeDiff(before, after)
        if (diff.isEmpty()) return
        save(memberId, actorId, AuditAction.UPDATE, diff)
    }

    @Transactional
    fun recordDelete(memberId: Long, actorId: Long, before: ChurchMemberSnapshot) {
        save(memberId, actorId, AuditAction.DELETE, fieldMap(before))
    }

    private fun save(memberId: Long, actorId: Long, action: AuditAction, diff: Map<String, Any?>) {
        val diffJson = encryptor.encrypt(objectMapper.writeValueAsString(diff))
        auditLogRepository.save(
            ChurchMemberAuditLog(
                churchMemberId = memberId,
                actorId = actorId,
                action = action,
                diffJson = diffJson,
            )
        )
    }

    private fun computeDiff(before: ChurchMemberSnapshot, after: ChurchMemberSnapshot): Map<String, List<Any?>> {
        val b = fieldMap(before); val a = fieldMap(after)
        return b.keys.filter { b[it] != a[it] }.associateWith { key -> listOf(b[key], a[key]) }
    }

    private fun fieldMap(s: ChurchMemberSnapshot): Map<String, Any?> = mapOf(
        "name" to s.name, "phone" to s.phone, "email" to s.email,
        "birthDate" to s.birthDate.toString(), "birthCalendar" to s.birthCalendar.name,
        "sex" to s.sex.name, "address" to s.address, "addressDetail" to s.addressDetail,
        "job" to s.job, "memo" to s.memo, "photoAssetId" to s.photoAssetId,
        "cellLabel" to s.cellLabel, "status" to s.status.name,
        "faithStage" to s.faithStage?.name, "office" to s.office.name,
        "officeAppointedAt" to s.officeAppointedAt?.toString(),
        "registeredAt" to s.registeredAt.toString(),
    )
}
