package org.happyzion.api.sms.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.sms.domain.SmsLog
import org.happyzion.api.sms.domain.SmsLogRecipient
import org.happyzion.api.sms.domain.SmsMessageType
import org.happyzion.api.sms.domain.SmsRecipientStatus
import org.happyzion.api.sms.infrastructure.client.AligoApiException
import org.happyzion.api.sms.infrastructure.client.AligoClient
import org.happyzion.api.sms.infrastructure.client.AligoSmsListItem
import org.happyzion.api.sms.infrastructure.client.AligoSmsListResult
import org.happyzion.api.sms.infrastructure.persistence.SmsLogRecipientRepository
import org.happyzion.api.sms.infrastructure.persistence.SmsLogRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.OffsetDateTime
import java.util.Optional

class SmsHistoryServiceTest {

    private val smsLogRepository: SmsLogRepository = mock()
    private val smsLogRecipientRepository: SmsLogRecipientRepository = mock()
    private val aligoClient: AligoClient = mock()

    private val service = SmsHistoryService(
        smsLogRepository = smsLogRepository,
        smsLogRecipientRepository = smsLogRecipientRepository,
        aligoClient = aligoClient,
    )

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun makeSmsLog(id: Long = 1L, aligoMsgId: String? = null): SmsLog {
        val log = SmsLog.create(
            msgType = SmsMessageType.SMS,
            sender = "01000000000",
            title = "테스트 제목",
            totalCount = 2,
            testMode = false,
            requestedBy = 99L,
        )
        val idField = SmsLog::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(log, id)
        if (aligoMsgId != null) log.aligoMsgId = aligoMsgId
        return log
    }

    private fun makeRecipient(
        id: Long = 1L,
        smsLogId: Long = 1L,
        phone: String = "01011111111",
        name: String? = "홍길동",
        message: String = "테스트 메시지",
        churchMemberId: Long? = null,
        status: SmsRecipientStatus = SmsRecipientStatus.PENDING,
        aligoSendState: String? = null,
    ): SmsLogRecipient {
        val recipient = SmsLogRecipient(
            smsLogId = smsLogId,
            phoneEnc = phone,
            phoneHash = "hash-$phone",
            receiverNameEnc = name,
            messageEnc = message,
            churchMemberId = churchMemberId,
            status = status,
        )
        val idField = SmsLogRecipient::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(recipient, id)
        if (aligoSendState != null) recipient.updateAligoState(aligoSendState)
        return recipient
    }

    // ─── listLogs() ───────────────────────────────────────────────────────────

    @Test
    fun `listLogs - maps page content to SmsLogPage`() {
        val log1 = makeSmsLog(1L)
        val log2 = makeSmsLog(2L)
        val pageable = PageRequest.of(0, 20)
        val page = PageImpl(listOf(log1, log2), pageable, 2L)

        whenever(smsLogRepository.findAllOrderedByRequestedAtDesc(any())).thenReturn(page)

        val result = service.listLogs(0, 20)

        assertThat(result.items).hasSize(2)
        assertThat(result.items[0].id).isEqualTo(1L)
        assertThat(result.items[1].id).isEqualTo(2L)
        assertThat(result.hasNext).isFalse()
    }

    @Test
    fun `listLogs - hasNext is true when more pages exist`() {
        val logs = (1L..20L).map { makeSmsLog(it) }
        val pageable = PageRequest.of(0, 20)
        val page = PageImpl(logs, pageable, 50L)

        whenever(smsLogRepository.findAllOrderedByRequestedAtDesc(any())).thenReturn(page)

        val result = service.listLogs(0, 20)

        assertThat(result.hasNext).isTrue()
    }

    @Test
    fun `listLogs - summary contains correct fields`() {
        val log = makeSmsLog(42L).also {
            it.aligoMsgId = "MSG-42"
            it.aligoResultCode = 1
            it.aligoMessage = "ok"
        }
        val pageable = PageRequest.of(0, 20)
        val page = PageImpl(listOf(log), pageable, 1L)

        whenever(smsLogRepository.findAllOrderedByRequestedAtDesc(any())).thenReturn(page)

        val result = service.listLogs(0, 20)

        val summary = result.items[0]
        assertThat(summary.id).isEqualTo(42L)
        assertThat(summary.aligoMsgId).isEqualTo("MSG-42")
        assertThat(summary.msgType).isEqualTo(SmsMessageType.SMS)
        assertThat(summary.sender).isEqualTo("01000000000")
        assertThat(summary.title).isEqualTo("테스트 제목")
        assertThat(summary.totalCount).isEqualTo(2)
        assertThat(summary.testMode).isFalse()
        assertThat(summary.requestedBy).isEqualTo(99L)
        assertThat(summary.aligoResultCode).isEqualTo(1)
        assertThat(summary.aligoMessage).isEqualTo("ok")
    }

    // ─── getDetail() without aligoMsgId ──────────────────────────────────────

    @Test
    fun `getDetail - returns recipients without calling aligoClient when aligoMsgId is null`() {
        val log = makeSmsLog(1L, aligoMsgId = null)
        val r1 = makeRecipient(1L, smsLogId = 1L, phone = "01011111111")
        val r2 = makeRecipient(2L, smsLogId = 1L, phone = "01022222222")

        whenever(smsLogRepository.findById(1L)).thenReturn(Optional.of(log))
        val pageable = PageRequest.of(0, 50)
        val recipPage = PageImpl(listOf(r1, r2), pageable, 2L)
        whenever(smsLogRecipientRepository.findAllBySmsLogId(1L, pageable)).thenReturn(recipPage)

        val detail = service.getDetail(1L, 0, 50)

        assertThat(detail.recipients).hasSize(2)
        assertThat(detail.hasNextRecipient).isFalse()
        assertThat(detail.log.id).isEqualTo(1L)

        verify(aligoClient, never()).smsList(any(), any(), any())
        verify(smsLogRecipientRepository, never()).saveAll(any<List<SmsLogRecipient>>())
    }

    @Test
    fun `getDetail - masks phone numbers correctly`() {
        val log = makeSmsLog(1L, aligoMsgId = null)
        val r11 = makeRecipient(1L, phone = "01011111111")  // 11 digits
        val r10 = makeRecipient(2L, phone = "0201111111")   // 10 digits
        val rShort = makeRecipient(3L, phone = "12345")     // short

        whenever(smsLogRepository.findById(1L)).thenReturn(Optional.of(log))
        val pageable = PageRequest.of(0, 50)
        val recipPage = PageImpl(listOf(r11, r10, rShort), pageable, 3L)
        whenever(smsLogRecipientRepository.findAllBySmsLogId(1L, pageable)).thenReturn(recipPage)

        val detail = service.getDetail(1L, 0, 50)

        assertThat(detail.recipients[0].phoneMasked).isEqualTo("010-****-1111")
        assertThat(detail.recipients[1].phoneMasked).isEqualTo("020-***-1111")
        assertThat(detail.recipients[2].phoneMasked).isEqualTo("***-****-2345")
    }

    @Test
    fun `getDetail - recipient detail contains correct fields`() {
        val log = makeSmsLog(1L, aligoMsgId = null)
        val r = makeRecipient(
            id = 7L,
            smsLogId = 1L,
            phone = "01099999999",
            name = "김철수",
            message = "안녕하세요",
            churchMemberId = 5L,
            status = SmsRecipientStatus.SENT,
            aligoSendState = "success",
        )

        whenever(smsLogRepository.findById(1L)).thenReturn(Optional.of(log))
        val pageable = PageRequest.of(0, 50)
        whenever(smsLogRecipientRepository.findAllBySmsLogId(1L, pageable))
            .thenReturn(PageImpl(listOf(r), pageable, 1L))

        val detail = service.getDetail(1L, 0, 50)

        val rd = detail.recipients[0]
        assertThat(rd.id).isEqualTo(7L)
        assertThat(rd.phoneMasked).isEqualTo("010-****-9999")
        assertThat(rd.name).isEqualTo("김철수")
        assertThat(rd.message).isEqualTo("안녕하세요")
        assertThat(rd.status).isEqualTo(SmsRecipientStatus.SENT)
        assertThat(rd.aligoSendState).isEqualTo("success")
        assertThat(rd.churchMemberId).isEqualTo(5L)
    }

    // ─── getDetail() with aligoMsgId enrichment ───────────────────────────────

    @Test
    fun `getDetail - enriches recipients from Aligo and saves changed ones`() {
        val log = makeSmsLog(1L, aligoMsgId = "MSG-001")
        val r1 = makeRecipient(1L, smsLogId = 1L, phone = "01011111111", aligoSendState = null)
        val r2 = makeRecipient(2L, smsLogId = 1L, phone = "01022222222", aligoSendState = null)

        whenever(smsLogRepository.findById(1L)).thenReturn(Optional.of(log))
        val pageable = PageRequest.of(0, 50)
        whenever(smsLogRecipientRepository.findAllBySmsLogId(1L, pageable))
            .thenReturn(PageImpl(listOf(r1, r2), pageable, 2L))

        val aligoResult = AligoSmsListResult(
            resultCode = 1,
            message = "success",
            list = listOf(
                AligoSmsListItem(mid = "MSG-001", smsState = "success", rphone = "01011111111"),
                AligoSmsListItem(mid = "MSG-001", smsState = "fail", rphone = "01022222222"),
            ),
        )
        whenever(aligoClient.smsList("MSG-001", 0, 600)).thenReturn(aligoResult)

        val detail = service.getDetail(1L, 0, 50)

        // Both recipients should be updated and saved
        val savedCaptor = argumentCaptor<List<SmsLogRecipient>>()
        verify(smsLogRecipientRepository).saveAll(savedCaptor.capture())
        val saved = savedCaptor.firstValue
        assertThat(saved).hasSize(2)

        // After enrichment, states should be updated
        assertThat(r1.aligoSendState).isEqualTo("success")
        assertThat(r1.status).isEqualTo(SmsRecipientStatus.SENT)
        assertThat(r2.aligoSendState).isEqualTo("fail")
        assertThat(r2.status).isEqualTo(SmsRecipientStatus.FAILED)
    }

    @Test
    fun `getDetail - skips recipients already having the same aligoSendState`() {
        val log = makeSmsLog(1L, aligoMsgId = "MSG-001")
        val r1 = makeRecipient(1L, phone = "01011111111", aligoSendState = "success")
        val r2 = makeRecipient(2L, phone = "01022222222", aligoSendState = null)

        whenever(smsLogRepository.findById(1L)).thenReturn(Optional.of(log))
        val pageable = PageRequest.of(0, 50)
        whenever(smsLogRecipientRepository.findAllBySmsLogId(1L, pageable))
            .thenReturn(PageImpl(listOf(r1, r2), pageable, 2L))

        val aligoResult = AligoSmsListResult(
            resultCode = 1,
            message = "success",
            list = listOf(
                AligoSmsListItem(mid = "MSG-001", smsState = "success", rphone = "01011111111"),
                AligoSmsListItem(mid = "MSG-001", smsState = "delivered", rphone = "01022222222"),
            ),
        )
        whenever(aligoClient.smsList("MSG-001", 0, 600)).thenReturn(aligoResult)

        service.getDetail(1L, 0, 50)

        // Only r2 should be in saveAll since r1 already had "success"
        val savedCaptor = argumentCaptor<List<SmsLogRecipient>>()
        verify(smsLogRecipientRepository).saveAll(savedCaptor.capture())
        val saved = savedCaptor.firstValue
        assertThat(saved).hasSize(1)
        assertThat(saved[0].id).isEqualTo(2L)
    }

    @Test
    fun `getDetail - unknown smsState calls markUnknown`() {
        val log = makeSmsLog(1L, aligoMsgId = "MSG-001")
        val r1 = makeRecipient(1L, phone = "01011111111", aligoSendState = null)

        whenever(smsLogRepository.findById(1L)).thenReturn(Optional.of(log))
        val pageable = PageRequest.of(0, 50)
        whenever(smsLogRecipientRepository.findAllBySmsLogId(1L, pageable))
            .thenReturn(PageImpl(listOf(r1), pageable, 1L))

        val aligoResult = AligoSmsListResult(
            resultCode = 1,
            message = "success",
            list = listOf(
                AligoSmsListItem(mid = "MSG-001", smsState = "pending", rphone = "01011111111"),
            ),
        )
        whenever(aligoClient.smsList("MSG-001", 0, 600)).thenReturn(aligoResult)

        service.getDetail(1L, 0, 50)

        assertThat(r1.status).isEqualTo(SmsRecipientStatus.UNKNOWN)
        assertThat(r1.aligoSendState).isEqualTo("pending")
    }

    @Test
    fun `getDetail - null smsState from Aligo treated as unknown`() {
        val log = makeSmsLog(1L, aligoMsgId = "MSG-001")
        val r1 = makeRecipient(1L, phone = "01011111111", aligoSendState = null)

        whenever(smsLogRepository.findById(1L)).thenReturn(Optional.of(log))
        val pageable = PageRequest.of(0, 50)
        whenever(smsLogRecipientRepository.findAllBySmsLogId(1L, pageable))
            .thenReturn(PageImpl(listOf(r1), pageable, 1L))

        val aligoResult = AligoSmsListResult(
            resultCode = 1,
            message = "success",
            list = listOf(
                AligoSmsListItem(mid = "MSG-001", smsState = null, rphone = "01011111111"),
            ),
        )
        whenever(aligoClient.smsList("MSG-001", 0, 600)).thenReturn(aligoResult)

        service.getDetail(1L, 0, 50)

        assertThat(r1.aligoSendState).isEqualTo("unknown")
        assertThat(r1.status).isEqualTo(SmsRecipientStatus.UNKNOWN)
    }

    @Test
    fun `getDetail - hasNextRecipient is true when page has more`() {
        val log = makeSmsLog(1L, aligoMsgId = null)
        val recipients = (1L..10L).map { makeRecipient(it, smsLogId = 1L) }
        val pageable = PageRequest.of(0, 10)
        // totalElements = 15 with pageSize = 10 => hasNext() = true
        val recipPage = PageImpl(recipients, pageable, 15L)

        whenever(smsLogRepository.findById(1L)).thenReturn(Optional.of(log))
        whenever(smsLogRecipientRepository.findAllBySmsLogId(1L, pageable)).thenReturn(recipPage)

        val result = service.getDetail(1L, 0, 10)

        assertThat(result.hasNextRecipient).isTrue()
    }

    // ─── getDetail() with AligoApiException ──────────────────────────────────

    @Test
    fun `getDetail - serves from DB when AligoApiException is thrown`() {
        val log = makeSmsLog(1L, aligoMsgId = "MSG-001")
        val r1 = makeRecipient(1L, phone = "01011111111", status = SmsRecipientStatus.SENT, aligoSendState = "success")

        whenever(smsLogRepository.findById(1L)).thenReturn(Optional.of(log))
        val pageable = PageRequest.of(0, 50)
        whenever(smsLogRecipientRepository.findAllBySmsLogId(1L, pageable))
            .thenReturn(PageImpl(listOf(r1), pageable, 1L))

        whenever(aligoClient.smsList("MSG-001", 0, 600))
            .thenThrow(AligoApiException(-1, "network error"))

        val detail = service.getDetail(1L, 0, 50)

        // Should still return the DB data
        assertThat(detail.recipients).hasSize(1)
        assertThat(detail.recipients[0].status).isEqualTo(SmsRecipientStatus.SENT)
        assertThat(detail.recipients[0].aligoSendState).isEqualTo("success")

        // Nothing should be saved since Aligo call failed
        verify(smsLogRecipientRepository, never()).saveAll(any<List<SmsLogRecipient>>())
    }

    // ─── getDetail() with nonexistent id ─────────────────────────────────────

    @Test
    fun `getDetail - throws NotFoundException when smsLogId does not exist`() {
        whenever(smsLogRepository.findById(999L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.getDetail(999L, 0, 50) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessageContaining("999")
    }
}
