package org.happyzion.api.member.infrastructure.persistence

import org.happyzion.api.member.domain.ChurchMemberAuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ChurchMemberAuditLogRepository : JpaRepository<ChurchMemberAuditLog, Long> {
    fun findByChurchMemberIdOrderByCreatedAtDescIdDesc(churchMemberId: Long, pageable: Pageable): Page<ChurchMemberAuditLog>
}
