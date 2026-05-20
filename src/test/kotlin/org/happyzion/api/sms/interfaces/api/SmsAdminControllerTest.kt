package org.happyzion.api.sms.interfaces.api

import jakarta.validation.Validation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.sms.application.SmsHistoryService
import org.happyzion.api.sms.application.SmsLogDetail
import org.happyzion.api.sms.application.SmsLogPage
import org.happyzion.api.sms.application.SmsLogRecipientDetail
import org.happyzion.api.sms.application.SmsLogSummary
import org.happyzion.api.sms.application.SmsSendOutcome
import org.happyzion.api.sms.application.SmsSendService
import org.happyzion.api.sms.domain.SmsMessageType
import org.happyzion.api.sms.domain.SmsRecipientStatus
import org.happyzion.api.sms.interfaces.dto.RawRecipientDto
import org.happyzion.api.sms.interfaces.dto.SmsBulkSendRequest
import org.happyzion.api.sms.interfaces.dto.SmsSendRequest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.OffsetDateTime

class SmsAdminControllerTest {

    private val smsSendService: SmsSendService = mock()
    private val smsHistoryService: SmsHistoryService = mock()
    private val controller = SmsAdminController(smsSendService, smsHistoryService)

    private val validator = Validation.buildDefaultValidatorFactory().validator

    private fun makeOutcome(logId: Long = 1L) = SmsSendOutcome(
        smsLogId = logId,
        aligoMsgId = "MSG-$logId",
        successCnt = 2,
        errorCnt = 0,
    )

    // ─── send() ───────────────────────────────────────────────────────────────

    @Test
    fun `send - delegates to service and maps outcome to response`() {
        whenever(smsSendService.send(any())).thenReturn(makeOutcome(55L))

        val request = SmsSendRequest(
            body = "안녕하세요",
            msgType = SmsMessageType.SMS,
            churchMemberIds = listOf(1L, 2L),
        )

        val response = controller.send(actorId = 42L, request = request)

        assertThat(response.smsLogId).isEqualTo(55L)
        assertThat(response.aligoMsgId).isEqualTo("MSG-55")
        assertThat(response.successCnt).isEqualTo(2)
        assertThat(response.errorCnt).isEqualTo(0)

        val cmdCaptor = argumentCaptor<org.happyzion.api.sms.application.SendCommand>()
        verify(smsSendService).send(cmdCaptor.capture())
        val cmd = cmdCaptor.firstValue
        assertThat(cmd.body).isEqualTo("안녕하세요")
        assertThat(cmd.msgType).isEqualTo(SmsMessageType.SMS)
        assertThat(cmd.churchMemberIds).containsExactly(1L, 2L)
        assertThat(cmd.requestedBy).isEqualTo(42L)
    }

    @Test
    fun `send - maps rawRecipients to command`() {
        whenever(smsSendService.send(any())).thenReturn(makeOutcome(1L))

        val request = SmsSendRequest(
            body = "직접 발송",
            msgType = SmsMessageType.SMS,
            rawRecipients = listOf(
                RawRecipientDto(phone = "01011111111", name = "홍길동"),
                RawRecipientDto(phone = "01022222222", name = null),
            ),
        )

        controller.send(actorId = 10L, request = request)

        val cmdCaptor = argumentCaptor<org.happyzion.api.sms.application.SendCommand>()
        verify(smsSendService).send(cmdCaptor.capture())
        val cmd = cmdCaptor.firstValue
        assertThat(cmd.rawRecipients).hasSize(2)
        assertThat(cmd.rawRecipients[0].phone).isEqualTo("01011111111")
        assertThat(cmd.rawRecipients[0].name).isEqualTo("홍길동")
        assertThat(cmd.rawRecipients[1].name).isNull()
    }

    // ─── sendBulk() ───────────────────────────────────────────────────────────

    @Test
    fun `sendBulk - delegates to service and maps outcome to response`() {
        whenever(smsSendService.sendBulk(any())).thenReturn(makeOutcome(77L))

        val request = SmsBulkSendRequest(
            msgType = SmsMessageType.LMS,
            rows = listOf(
                SmsBulkSendRequest.BulkRowDto(phone = "01011111111", message = "개별 메시지"),
            ),
        )

        val response = controller.sendBulk(actorId = 42L, request = request)

        assertThat(response.smsLogId).isEqualTo(77L)
        assertThat(response.aligoMsgId).isEqualTo("MSG-77")

        val cmdCaptor = argumentCaptor<org.happyzion.api.sms.application.BulkSendCommand>()
        verify(smsSendService).sendBulk(cmdCaptor.capture())
        val cmd = cmdCaptor.firstValue
        assertThat(cmd.msgType).isEqualTo(SmsMessageType.LMS)
        assertThat(cmd.rows).hasSize(1)
        assertThat(cmd.rows[0].phone).isEqualTo("01011111111")
        assertThat(cmd.rows[0].message).isEqualTo("개별 메시지")
        assertThat(cmd.requestedBy).isEqualTo(42L)
    }

    // ─── Validation: SmsSendRequest ───────────────────────────────────────────

    @Test
    fun `SmsSendRequest - hasAnyRecipient fails when both churchMemberIds and rawRecipients are empty`() {
        val request = SmsSendRequest(
            body = "메시지",
            msgType = SmsMessageType.SMS,
            churchMemberIds = emptyList(),
            rawRecipients = emptyList(),
        )

        val violations = validator.validate(request)
        val messages = violations.map { it.message }
        assertThat(messages).anyMatch { it.contains("수신자") }
    }

    @Test
    fun `SmsSendRequest - passes when churchMemberIds has entries`() {
        val request = SmsSendRequest(
            body = "메시지",
            msgType = SmsMessageType.SMS,
            churchMemberIds = listOf(1L),
            rawRecipients = emptyList(),
        )

        val violations = validator.validate(request)
        // Filter only hasAnyRecipient-related violations
        val recipientViolations = violations.filter { it.propertyPath.toString().contains("hasAnyRecipient") }
        assertThat(recipientViolations).isEmpty()
    }

    @Test
    fun `SmsSendRequest - passes when rawRecipients has entries`() {
        val request = SmsSendRequest(
            body = "메시지",
            msgType = SmsMessageType.SMS,
            churchMemberIds = emptyList(),
            rawRecipients = listOf(RawRecipientDto(phone = "01011111111")),
        )

        val violations = validator.validate(request)
        val recipientViolations = violations.filter { it.propertyPath.toString().contains("hasAnyRecipient") }
        assertThat(recipientViolations).isEmpty()
    }

    @Test
    fun `SmsSendRequest - body blank fails validation`() {
        val request = SmsSendRequest(
            body = "   ",
            msgType = SmsMessageType.SMS,
            churchMemberIds = listOf(1L),
        )

        val violations = validator.validate(request)
        val bodyViolations = violations.filter { it.propertyPath.toString() == "body" }
        assertThat(bodyViolations).isNotEmpty()
    }

    // ─── Validation: BulkRowDto ───────────────────────────────────────────────

    @Test
    fun `BulkRowDto - hasIdentifier fails when churchMemberId is null and phone is blank`() {
        val row = SmsBulkSendRequest.BulkRowDto(
            churchMemberId = null,
            phone = null,
            name = null,
            message = "메시지",
        )

        val violations = validator.validate(row)
        val messages = violations.map { it.message }
        assertThat(messages).anyMatch { it.contains("churchMemberId") || it.contains("phone") }
    }

    @Test
    fun `BulkRowDto - passes when churchMemberId is set`() {
        val row = SmsBulkSendRequest.BulkRowDto(
            churchMemberId = 5L,
            phone = null,
            message = "메시지",
        )

        val violations = validator.validate(row)
        val identifierViolations = violations.filter { it.propertyPath.toString().contains("hasIdentifier") }
        assertThat(identifierViolations).isEmpty()
    }

    @Test
    fun `BulkRowDto - passes when phone is set`() {
        val row = SmsBulkSendRequest.BulkRowDto(
            churchMemberId = null,
            phone = "01011111111",
            message = "메시지",
        )

        val violations = validator.validate(row)
        val identifierViolations = violations.filter { it.propertyPath.toString().contains("hasIdentifier") }
        assertThat(identifierViolations).isEmpty()
    }

    @Test
    fun `BulkRowDto - message blank fails validation`() {
        val row = SmsBulkSendRequest.BulkRowDto(
            churchMemberId = 1L,
            phone = null,
            message = "",
        )

        val violations = validator.validate(row)
        val messageViolations = violations.filter { it.propertyPath.toString() == "message" }
        assertThat(messageViolations).isNotEmpty()
    }

    @Test
    fun `RawRecipientDto - phone must match digit pattern`() {
        val tooShort = RawRecipientDto(phone = "0101234")  // 7 digits
        val valid = RawRecipientDto(phone = "01012345678") // 11 digits

        assertThat(validator.validate(tooShort).map { it.propertyPath.toString() })
            .contains("phone")
        assertThat(validator.validate(valid)).isEmpty()
    }

    // ─── listLogs() ───────────────────────────────────────────────────────────

    @Test
    fun `listLogs - delegates to smsHistoryService and returns page response`() {
        val now = OffsetDateTime.now()
        val summary = SmsLogSummary(
            id = 1L,
            aligoMsgId = "MSG-001",
            msgType = SmsMessageType.SMS,
            sender = "01000000000",
            title = null,
            totalCount = 5,
            successCount = 4,
            errorCount = 1,
            testMode = false,
            requestedBy = 99L,
            requestedAt = now,
            aligoResultCode = 1,
            aligoMessage = "success",
        )
        val logPage = SmsLogPage(items = listOf(summary), hasNext = false)

        whenever(smsHistoryService.listLogs(0, 20)).thenReturn(logPage)

        val response = controller.listLogs(page = 0, pageSize = 20)

        assertThat(response.items).hasSize(1)
        assertThat(response.items[0].id).isEqualTo(1L)
        assertThat(response.items[0].aligoMsgId).isEqualTo("MSG-001")
        assertThat(response.items[0].totalCount).isEqualTo(5)
        assertThat(response.hasNext).isFalse()

        verify(smsHistoryService).listLogs(0, 20)
    }

    @Test
    fun `listLogs - passes page and pageSize params to service`() {
        val logPage = SmsLogPage(items = emptyList(), hasNext = true)
        whenever(smsHistoryService.listLogs(2, 10)).thenReturn(logPage)

        val response = controller.listLogs(page = 2, pageSize = 10)

        assertThat(response.hasNext).isTrue()
        verify(smsHistoryService).listLogs(2, 10)
    }

    // ─── getDetail() ──────────────────────────────────────────────────────────

    @Test
    fun `getDetail - delegates to smsHistoryService and returns detail response`() {
        val now = OffsetDateTime.now()
        val summary = SmsLogSummary(
            id = 10L,
            aligoMsgId = "MSG-010",
            msgType = SmsMessageType.LMS,
            sender = "01000000000",
            title = "제목",
            totalCount = 3,
            successCount = 3,
            errorCount = 0,
            testMode = false,
            requestedBy = 99L,
            requestedAt = now,
            aligoResultCode = 1,
            aligoMessage = "ok",
        )
        val recipientDetail = SmsLogRecipientDetail(
            id = 5L,
            phoneMasked = "010-****-1111",
            name = "홍길동",
            message = "안녕하세요",
            status = SmsRecipientStatus.SENT,
            aligoSendState = "success",
            churchMemberId = 3L,
        )
        val logDetail = SmsLogDetail(
            log = summary,
            recipients = listOf(recipientDetail),
            hasNextRecipient = false,
        )

        whenever(smsHistoryService.getDetail(10L, 0, 50)).thenReturn(logDetail)

        val response = controller.getDetail(smsLogId = 10L, page = 0, pageSize = 50)

        assertThat(response.log.id).isEqualTo(10L)
        assertThat(response.log.aligoMsgId).isEqualTo("MSG-010")
        assertThat(response.recipients).hasSize(1)
        assertThat(response.recipients[0].id).isEqualTo(5L)
        assertThat(response.recipients[0].phoneMasked).isEqualTo("010-****-1111")
        assertThat(response.recipients[0].name).isEqualTo("홍길동")
        assertThat(response.recipients[0].status).isEqualTo(SmsRecipientStatus.SENT)
        assertThat(response.hasNextRecipient).isFalse()

        verify(smsHistoryService).getDetail(10L, 0, 50)
    }

    @Test
    fun `getDetail - NotFoundException propagates when smsLogId not found`() {
        whenever(smsHistoryService.getDetail(999L, 0, 50))
            .thenThrow(NotFoundException("SmsLog not found: 999"))

        assertThatThrownBy { controller.getDetail(smsLogId = 999L, page = 0, pageSize = 50) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessageContaining("999")
    }
}
