package org.happyzion.api.sms.infrastructure.persistence

import org.happyzion.api.sms.domain.SmsLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface SmsLogRepository : JpaRepository<SmsLog, Long> {
    @Query("select s from SmsLog s order by s.requestedAt desc, s.id desc")
    fun findAllOrderedByRequestedAtDesc(pageable: Pageable): Page<SmsLog>
}
