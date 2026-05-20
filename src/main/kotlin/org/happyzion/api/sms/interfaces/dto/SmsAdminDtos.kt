package org.happyzion.api.sms.interfaces.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.happyzion.api.sms.application.BulkRow
import org.happyzion.api.sms.application.BulkSendCommand
import org.happyzion.api.sms.application.RawRecipient
import org.happyzion.api.sms.application.SendCommand
import org.happyzion.api.sms.application.SmsLogDetail
import org.happyzion.api.sms.application.SmsLogPage
import org.happyzion.api.sms.application.SmsLogSummary
import org.happyzion.api.sms.application.SmsSendOutcome
import org.happyzion.api.sms.domain.SmsMessageType
import org.happyzion.api.sms.domain.SmsRecipientStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

// ─── Request DTOs ──────────────────────────────────────────────────────────────

data class SmsSendRequest(
    @field:NotBlank val body: String,
    @field:NotNull val msgType: SmsMessageType,
    val title: String? = null,
    val sender: String? = null,
    val testMode: Boolean? = null,
    val scheduledDate: LocalDate? = null,
    val scheduledTime: LocalTime? = null,
    val churchMemberIds: List<Long> = emptyList(),
    @field:Valid
    val rawRecipients: List<RawRecipientDto> = emptyList(),
) {
    @get:AssertTrue(message = "수신자(교인 ID 또는 직접 입력 번호) 최소 1명 이상 필요")
    val hasAnyRecipient: Boolean
        get() = churchMemberIds.isNotEmpty() || rawRecipients.isNotEmpty()
}

data class RawRecipientDto(
    @field:NotBlank
    @field:Pattern(regexp = "^\\d{10,11}$")
    val phone: String,
    val name: String? = null,
)

data class SmsBulkSendRequest(
    @field:NotNull val msgType: SmsMessageType,
    @field:NotEmpty
    @field:Size(max = 500)
    @field:Valid
    val rows: List<BulkRowDto>,
    val title: String? = null,
    val sender: String? = null,
    val testMode: Boolean? = null,
    val scheduledDate: LocalDate? = null,
    val scheduledTime: LocalTime? = null,
) {
    data class BulkRowDto(
        val churchMemberId: Long? = null,
        val phone: String? = null,
        val name: String? = null,
        @field:NotBlank val message: String,
    ) {
        @get:AssertTrue(message = "churchMemberId 또는 phone 중 하나는 필수")
        val hasIdentifier: Boolean
            get() = churchMemberId != null || !phone.isNullOrBlank()
    }
}

// ─── Response DTO ──────────────────────────────────────────────────────────────

data class SmsSendResponse(
    val smsLogId: Long,
    val aligoMsgId: String?,
    val successCnt: Int,
    val errorCnt: Int,
)

// ─── Mapping extensions ────────────────────────────────────────────────────────

fun SmsSendRequest.toCommand(actorId: Long) = SendCommand(
    body = body,
    msgType = msgType,
    title = title,
    sender = sender,
    testMode = testMode,
    scheduledDate = scheduledDate,
    scheduledTime = scheduledTime,
    churchMemberIds = churchMemberIds,
    rawRecipients = rawRecipients.map { RawRecipient(it.phone, it.name) },
    requestedBy = actorId,
)

fun SmsBulkSendRequest.toCommand(actorId: Long) = BulkSendCommand(
    msgType = msgType,
    rows = rows.map { row ->
        BulkRow(
            churchMemberId = row.churchMemberId,
            phone = row.phone,
            name = row.name,
            message = row.message,
        )
    },
    title = title,
    sender = sender,
    testMode = testMode,
    scheduledDate = scheduledDate,
    scheduledTime = scheduledTime,
    requestedBy = actorId,
)

fun SmsSendOutcome.toResponse() = SmsSendResponse(
    smsLogId = smsLogId,
    aligoMsgId = aligoMsgId,
    successCnt = successCnt,
    errorCnt = errorCnt,
)

// ─── History Response DTOs ─────────────────────────────────────────────────────

data class SmsLogSummaryResponse(
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

data class SmsLogPageResponse(
    val items: List<SmsLogSummaryResponse>,
    val hasNext: Boolean,
)

data class SmsLogRecipientDetailResponse(
    val id: Long,
    val phoneMasked: String,
    val name: String?,
    val message: String,
    val status: SmsRecipientStatus,
    val aligoSendState: String?,
    val churchMemberId: Long?,
)

data class SmsLogDetailResponse(
    val log: SmsLogSummaryResponse,
    val recipients: List<SmsLogRecipientDetailResponse>,
    val hasNextRecipient: Boolean,
)

// ─── History Mapping extensions ────────────────────────────────────────────────

fun SmsLogSummary.toResponse() = SmsLogSummaryResponse(
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

fun SmsLogDetail.toResponse() = SmsLogDetailResponse(
    log = log.toResponse(),
    recipients = recipients.map { r ->
        SmsLogRecipientDetailResponse(
            id = r.id,
            phoneMasked = r.phoneMasked,
            name = r.name,
            message = r.message,
            status = r.status,
            aligoSendState = r.aligoSendState,
            churchMemberId = r.churchMemberId,
        )
    },
    hasNextRecipient = hasNextRecipient,
)

fun SmsLogPage.toResponse() = SmsLogPageResponse(
    items = items.map { it.toResponse() },
    hasNext = hasNext,
)
