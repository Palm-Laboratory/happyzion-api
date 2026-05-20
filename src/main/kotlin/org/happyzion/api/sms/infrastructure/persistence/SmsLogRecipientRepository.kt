package org.happyzion.api.sms.infrastructure.persistence

import org.happyzion.api.sms.domain.SmsLogRecipient
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface SmsLogRecipientRepository : JpaRepository<SmsLogRecipient, Long> {
    fun findAllBySmsLogId(smsLogId: Long, pageable: Pageable): Page<SmsLogRecipient>
}
