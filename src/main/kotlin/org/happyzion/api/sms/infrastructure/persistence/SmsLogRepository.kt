package org.happyzion.api.sms.infrastructure.persistence

import org.happyzion.api.sms.domain.SmsLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface SmsLogRepository : JpaRepository<SmsLog, Long> {
    fun findByOrderByRequestedAtDescIdDesc(pageable: Pageable): Page<SmsLog>
}
