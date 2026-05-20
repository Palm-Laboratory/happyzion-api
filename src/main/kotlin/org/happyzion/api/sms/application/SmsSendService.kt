package org.happyzion.api.sms.application

import org.happyzion.api.member.infrastructure.persistence.ChurchMemberRepository
import org.happyzion.api.sms.domain.SmsMessageType
import org.happyzion.api.sms.infrastructure.client.AligoApiException
import org.happyzion.api.sms.infrastructure.client.AligoClient
import org.happyzion.api.sms.infrastructure.client.AligoProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter

@Service
class SmsSendService(
    private val aligoClient: AligoClient,
    private val aligoProperties: AligoProperties,
    private val churchMemberRepository: ChurchMemberRepository,
    private val recipientResolver: SmsRecipientResolver,
    private val smsLogPersister: SmsLogPersister,
) {

    private val log = LoggerFactory.getLogger(SmsSendService::class.java)

    private val rdateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val rtimeFormatter = DateTimeFormatter.ofPattern("HHmm")

    fun send(cmd: SendCommand): SmsSendOutcome {
        val effectiveSender = cmd.sender ?: aligoProperties.sender
        val effectiveTestMode = cmd.testMode ?: aligoProperties.testmode

        val recipients = recipientResolver.resolve(cmd.churchMemberIds, cmd.rawRecipients)
        require(recipients.isNotEmpty()) { "수신자가 없습니다." }

        // Pre-save (REQUIRES_NEW transaction — commits before Aligo call)
        val smsLog = smsLogPersister.preSaveSend(
            msgType = cmd.msgType,
            sender = effectiveSender,
            title = cmd.title,
            testMode = effectiveTestMode,
            requestedBy = cmd.requestedBy,
            recipients = recipients,
            messageBody = cmd.body,
        )

        // Build Aligo params
        val params = mutableMapOf<String, String>()
        params["sender"] = effectiveSender
        params["receiver"] = recipients.joinToString(",") { it.phone }
        params["msg"] = cmd.body
        if (cmd.msgType != SmsMessageType.SMS) {
            params["msg_type"] = cmd.msgType.name
        }
        cmd.title?.let { params["title"] = it }

        // destination: "phone|name,phone|name,..." — only include if body uses %고객명% and at least one name exists
        val hasNamePlaceholder = cmd.body.contains("%고객명%")
        val hasAnyName = recipients.any { it.name != null }
        if (hasNamePlaceholder && hasAnyName) {
            params["destination"] = recipients.joinToString(",") { r ->
                if (r.name != null) "${r.phone}|${r.name}" else r.phone
            }
        }

        cmd.scheduledDate?.let { params["rdate"] = it.format(rdateFormatter) }
        cmd.scheduledTime?.let { params["rtime"] = it.format(rtimeFormatter) }

        // Call Aligo and update logs
        return try {
            val result = aligoClient.send(params)
            try {
                smsLogPersister.postUpdateSuccess(
                    smsLogId = smsLog.id,
                    msgId = result.msgId,
                    successCnt = result.successCnt,
                    errorCnt = result.errorCnt,
                    resultCode = result.resultCode,
                    message = result.message,
                )
            } catch (e: Exception) {
                log.error("Failed to update SmsLog after successful Aligo send (smsLogId=${smsLog.id})", e)
            }
            SmsSendOutcome(
                smsLogId = smsLog.id,
                aligoMsgId = result.msgId,
                successCnt = result.successCnt,
                errorCnt = result.errorCnt,
            )
        } catch (e: AligoApiException) {
            try {
                smsLogPersister.postUpdateFailed(
                    smsLogId = smsLog.id,
                    resultCode = e.resultCode,
                    message = e.message,
                )
            } catch (updateEx: Exception) {
                log.error("Failed to update SmsLog after Aligo API error (smsLogId=${smsLog.id})", updateEx)
            }
            throw e
        }
    }

    fun sendBulk(cmd: BulkSendCommand): SmsSendOutcome {
        val effectiveSender = cmd.sender ?: aligoProperties.sender
        val effectiveTestMode = cmd.testMode ?: aligoProperties.testmode

        // Resolve each row, batch-loading church members
        val resolvedRows = resolveBulkRows(cmd.rows)
        require(resolvedRows.isNotEmpty()) { "수신자가 없습니다." }

        // Pre-save (REQUIRES_NEW transaction)
        val smsLog = smsLogPersister.preSaveBulk(
            msgType = cmd.msgType,
            sender = effectiveSender,
            title = cmd.title,
            testMode = effectiveTestMode,
            requestedBy = cmd.requestedBy,
            rows = resolvedRows,
        )

        // Build Aligo params for /send_mass/
        val params = mutableMapOf<String, String>()
        params["sender"] = effectiveSender
        // Aligo /send_mass/ requires msg_type to be explicit even for SMS — always set it.
        params["msg_type"] = cmd.msgType.name
        params["cnt"] = resolvedRows.size.toString()
        resolvedRows.forEachIndexed { i, row ->
            val idx = i + 1
            params["rec_$idx"] = row.phone
            params["msg_$idx"] = row.message
        }
        cmd.title?.let { params["title"] = it }
        cmd.scheduledDate?.let { params["rdate"] = it.format(rdateFormatter) }
        cmd.scheduledTime?.let { params["rtime"] = it.format(rtimeFormatter) }

        // Call Aligo and update logs
        return try {
            val result = aligoClient.sendMass(params)
            try {
                smsLogPersister.postUpdateSuccess(
                    smsLogId = smsLog.id,
                    msgId = result.msgId,
                    successCnt = result.successCnt,
                    errorCnt = result.errorCnt,
                    resultCode = result.resultCode,
                    message = result.message,
                )
            } catch (e: Exception) {
                log.error("Failed to update SmsLog after successful Aligo sendMass (smsLogId=${smsLog.id})", e)
            }
            SmsSendOutcome(
                smsLogId = smsLog.id,
                aligoMsgId = result.msgId,
                successCnt = result.successCnt,
                errorCnt = result.errorCnt,
            )
        } catch (e: AligoApiException) {
            try {
                smsLogPersister.postUpdateFailed(
                    smsLogId = smsLog.id,
                    resultCode = e.resultCode,
                    message = e.message,
                )
            } catch (updateEx: Exception) {
                log.error("Failed to update SmsLog after Aligo API error (smsLogId=${smsLog.id})", updateEx)
            }
            throw e
        }
    }

    private fun resolveBulkRows(rows: List<BulkRow>): List<BulkRecipientRow> {
        // Batch-load all referenced church members
        val memberIds = rows.mapNotNull { it.churchMemberId }
        val membersById = if (memberIds.isNotEmpty()) {
            churchMemberRepository.findAllById(memberIds).associateBy { it.id }
        } else emptyMap()

        return rows.mapNotNull { row ->
            when {
                row.churchMemberId != null -> {
                    val member = membersById[row.churchMemberId] ?: return@mapNotNull null
                    BulkRecipientRow(
                        phone = member.phone,
                        name = member.name,
                        message = row.message,
                        churchMemberId = row.churchMemberId,
                    )
                }
                !row.phone.isNullOrBlank() -> {
                    BulkRecipientRow(
                        phone = row.phone,
                        name = row.name,
                        message = row.message,
                        churchMemberId = null,
                    )
                }
                else -> null
            }
        }
    }
}
