package org.happyzion.api.sms.application

import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.sms.domain.SmsLog
import org.happyzion.api.sms.infrastructure.client.AligoApiException
import org.happyzion.api.sms.infrastructure.client.AligoClient
import org.happyzion.api.sms.infrastructure.persistence.SmsLogRecipientRepository
import org.happyzion.api.sms.infrastructure.persistence.SmsLogRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SmsHistoryService(
    private val smsLogRepository: SmsLogRepository,
    private val smsLogRecipientRepository: SmsLogRecipientRepository,
    private val aligoClient: AligoClient,
) {

    private val logger = LoggerFactory.getLogger(SmsHistoryService::class.java)

    companion object {
        private const val ALIGO_STATE_UNKNOWN = "unknown"
    }

    fun listLogs(page: Int, pageSize: Int): SmsLogPage {
        val result = smsLogRepository.findAllOrderedByRequestedAtDesc(PageRequest.of(page, pageSize))
        return SmsLogPage(
            items = result.content.map { it.toSummary() },
            hasNext = result.hasNext(),
        )
    }

    @Transactional
    fun getDetail(smsLogId: Long, recipientPage: Int, recipientPageSize: Int): SmsLogDetail {
        val smsLog = smsLogRepository.findById(smsLogId).orElseThrow {
            NotFoundException("SmsLog not found: $smsLogId")
        }

        val recipientsPage = smsLogRecipientRepository.findAllBySmsLogId(
            smsLogId,
            PageRequest.of(recipientPage, recipientPageSize),
        )
        val recipients = recipientsPage.content.toMutableList()

        if (smsLog.aligoMsgId != null) {
            val aligoResult = try {
                // 600 exceeds the max batch size (500 for /send_mass/); fetching a single page is sufficient.
                // If limits change, implement pagination here.
                aligoClient.smsList(smsLog.aligoMsgId!!, 0, 600)
            } catch (e: AligoApiException) {
                logger.warn("Aligo smsList enrichment failed for msgId={}: {}", smsLog.aligoMsgId, e.message)
                null
            }

            if (aligoResult != null) {
                val aligoByPhone = aligoResult.list.associateBy {
                    it.rphone?.filter(Char::isDigit) ?: ""
                }
                val toSave = recipients.filter { r ->
                    val phone = r.phoneEnc.filter(Char::isDigit)
                    val aligoItem = aligoByPhone[phone]
                    if (aligoItem != null) {
                        val newState = aligoItem.smsState ?: ALIGO_STATE_UNKNOWN
                        if (r.aligoSendState != newState) {
                            r.updateAligoState(newState)
                            when (newState.lowercase()) {
                                "success", "delivered" -> r.markSent()
                                "fail", "failed", "error" -> r.markFailed()
                                else -> r.markUnknown()
                            }
                            true
                        } else false
                    } else false
                }
                if (toSave.isNotEmpty()) smsLogRecipientRepository.saveAll(toSave)
            }
        }

        return SmsLogDetail(
            log = smsLog.toSummary(),
            recipients = recipients.map { r ->
                SmsLogRecipientDetail(
                    id = r.id,
                    phoneMasked = maskPhone(r.phoneEnc),
                    name = r.receiverNameEnc,
                    message = r.messageEnc,
                    status = r.status,
                    aligoSendState = r.aligoSendState,
                    churchMemberId = r.churchMemberId,
                )
            },
            hasNextRecipient = recipientsPage.hasNext(),
        )
    }

    private fun SmsLog.toSummary() = SmsLogSummary(
        id = id,
        aligoMsgId = aligoMsgId,
        msgType = msgType,
        sender = sender,
        title = title,
        totalCount = totalCount,
        successCount = successCount,
        errorCount = errorCount,
        testMode = testMode,
        requestedBy = requestedBy,
        requestedAt = requestedAt,
        aligoResultCode = aligoResultCode,
        aligoMessage = aligoMessage,
    )
}

private fun maskPhone(phone: String): String {
    val digits = phone.filter(Char::isDigit)
    return when {
        digits.length == 11 -> "${digits.substring(0, 3)}-****-${digits.takeLast(4)}"
        digits.length == 10 -> "${digits.substring(0, 3)}-***-${digits.takeLast(4)}"
        else -> "***-****-${digits.takeLast(4)}"
    }
}
