package org.happyzion.api.sms.application

import org.happyzion.api.sms.domain.SmsMessageType
import org.happyzion.api.sms.domain.SmsRecipientStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

data class SendCommand(
    val body: String,
    val msgType: SmsMessageType,
    val title: String?,
    val sender: String?,
    val testMode: Boolean?,
    val scheduledDate: LocalDate?,
    val scheduledTime: LocalTime?,
    val churchMemberIds: List<Long>,
    val rawRecipients: List<RawRecipient>,
    val requestedBy: Long,
)

data class BulkSendCommand(
    val msgType: SmsMessageType,
    val rows: List<BulkRow>,
    val title: String?,
    val sender: String?,
    val testMode: Boolean?,
    val scheduledDate: LocalDate?,
    val scheduledTime: LocalTime?,
    val requestedBy: Long,
)

data class BulkRow(
    val churchMemberId: Long?,
    val phone: String?,
    val name: String?,
    val message: String,
)

data class RawRecipient(val phone: String, val name: String?)

// ─── History models ────────────────────────────────────────────────────────────

data class SmsLogSummary(
    val id: Long,
    val aligoMsgId: String?,
    val msgType: SmsMessageType,
    val sender: String,
    val title: String?,
    val totalCount: Int,
    val successCount: Int,
    val errorCount: Int,
    val testMode: Boolean,
    val requestedBy: Long,
    val requestedAt: OffsetDateTime,
    val aligoResultCode: Int?,
    val aligoMessage: String?,
)

data class SmsLogPage(
    val items: List<SmsLogSummary>,
    val hasNext: Boolean,
)

data class SmsLogRecipientDetail(
    val id: Long,
    val phoneMasked: String,
    val name: String?,
    val message: String,
    val status: SmsRecipientStatus,
    val aligoSendState: String?,
    val churchMemberId: Long?,
)

data class SmsLogDetail(
    val log: SmsLogSummary,
    val recipients: List<SmsLogRecipientDetail>,
    val hasNextRecipient: Boolean,
)
