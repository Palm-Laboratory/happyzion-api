package org.happyzion.api.sms.application

import org.happyzion.api.common.security.pii.MemberSearchKeyNormalizer
import org.happyzion.api.common.security.pii.PiiHasher
import org.happyzion.api.sms.domain.SmsLog
import org.happyzion.api.sms.domain.SmsLogRecipient
import org.happyzion.api.sms.domain.SmsMessageType
import org.happyzion.api.sms.infrastructure.persistence.SmsLogRecipientRepository
import org.happyzion.api.sms.infrastructure.persistence.SmsLogRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class SmsLogPersister(
    private val smsLogRepository: SmsLogRepository,
    private val smsLogRecipientRepository: SmsLogRecipientRepository,
    private val piiHasher: PiiHasher,
    private val normalizer: MemberSearchKeyNormalizer,
) {

    /**
     * Pre-save for /send/ — one message body shared across all recipients.
     * Runs in its own transaction (REQUIRES_NEW) so the INSERT is committed before the Aligo call.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun preSaveSend(
        msgType: SmsMessageType,
        sender: String,
        title: String?,
        testMode: Boolean,
        requestedBy: Long,
        recipients: List<ResolvedRecipient>,
        messageBody: String,
    ): SmsLog {
        val log = SmsLog.create(
            msgType = msgType,
            sender = sender,
            title = title,
            totalCount = recipients.size,
            testMode = testMode,
            requestedBy = requestedBy,
        )
        val savedLog = smsLogRepository.save(log)

        val recipientEntities = recipients.map { r ->
            SmsLogRecipient(
                smsLogId = savedLog.id,
                phoneEnc = r.phone,
                phoneHash = piiHasher.hash(normalizer.forStoredPhone(r.phone)),
                receiverNameEnc = r.name,
                messageEnc = messageBody,
                churchMemberId = r.churchMemberId,
            )
        }
        smsLogRecipientRepository.saveAll(recipientEntities)

        return savedLog
    }

    /**
     * Pre-save for /send_mass/ — each recipient has their own message.
     * Runs in its own transaction (REQUIRES_NEW).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun preSaveBulk(
        msgType: SmsMessageType,
        sender: String,
        title: String?,
        testMode: Boolean,
        requestedBy: Long,
        rows: List<BulkRecipientRow>,
    ): SmsLog {
        val log = SmsLog.create(
            msgType = msgType,
            sender = sender,
            title = title,
            totalCount = rows.size,
            testMode = testMode,
            requestedBy = requestedBy,
        )
        val savedLog = smsLogRepository.save(log)

        val recipientEntities = rows.map { row ->
            SmsLogRecipient(
                smsLogId = savedLog.id,
                phoneEnc = row.phone,
                phoneHash = piiHasher.hash(normalizer.forStoredPhone(row.phone)),
                receiverNameEnc = row.name,
                messageEnc = row.message,
                churchMemberId = row.churchMemberId,
            )
        }
        smsLogRecipientRepository.saveAll(recipientEntities)

        return savedLog
    }

    /**
     * Post-update: update the SmsLog after Aligo responds, and mark all recipients SENT.
     * Runs in its own transaction (REQUIRES_NEW).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun postUpdateSuccess(smsLogId: Long, msgId: String?, successCnt: Int, errorCnt: Int, resultCode: Int, message: String) {
        val log = smsLogRepository.findById(smsLogId).orElse(null) ?: return
        log.updateAfterSend(msgId, successCnt, errorCnt, resultCode, message)
        smsLogRepository.save(log)

        // PageRequest capped at 600: Aligo bulk limit is 500, +100 headroom to avoid unbounded load
        val recipients = smsLogRecipientRepository.findAllBySmsLogId(smsLogId, PageRequest.of(0, 600))
        recipients.forEach { it.markSent() }
        smsLogRecipientRepository.saveAll(recipients)
    }

    /**
     * Post-update: mark the SmsLog as failed and all recipients FAILED.
     * Runs in its own transaction (REQUIRES_NEW).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun postUpdateFailed(smsLogId: Long, resultCode: Int, message: String) {
        val log = smsLogRepository.findById(smsLogId).orElse(null) ?: return
        log.updateAfterSend(null, 0, log.totalCount, resultCode, message)
        smsLogRepository.save(log)

        // PageRequest capped at 600: Aligo bulk limit is 500, +100 headroom to avoid unbounded load
        val recipients = smsLogRecipientRepository.findAllBySmsLogId(smsLogId, PageRequest.of(0, 600))
        recipients.forEach { it.markFailed() }
        smsLogRecipientRepository.saveAll(recipients)
    }
}

data class BulkRecipientRow(
    val phone: String,
    val name: String?,
    val message: String,
    val churchMemberId: Long?,
)
