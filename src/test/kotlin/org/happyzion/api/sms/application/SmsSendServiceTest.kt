package org.happyzion.api.sms.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.happyzion.api.common.security.pii.MemberSearchKeyNormalizer
import org.happyzion.api.common.security.pii.PiiEncryptionProperties
import org.happyzion.api.common.security.pii.PiiHasher
import org.happyzion.api.member.infrastructure.persistence.ChurchMemberRepository
import org.happyzion.api.sms.domain.SmsLog
import org.happyzion.api.sms.domain.SmsMessageType
import org.happyzion.api.sms.infrastructure.client.AligoApiException
import org.happyzion.api.sms.infrastructure.client.AligoClient
import org.happyzion.api.sms.infrastructure.client.AligoProperties
import org.happyzion.api.sms.infrastructure.client.AligoSendResult
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class SmsSendServiceTest {

    private val hashKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val piiProps = PiiEncryptionProperties("v1:$hashKey", "v1", hashKey)
    private val piiHasher = PiiHasher(piiProps)
    private val normalizer = MemberSearchKeyNormalizer()

    private val aligoClient: AligoClient = mock()
    private val aligoProperties = AligoProperties(
        baseUrl = "https://apis.aligo.in",
        userId = "test-user",
        apiKey = "test-key",
        sender = "01000000000",
        testmode = false,
    )
    private val churchMemberRepository: ChurchMemberRepository = mock()
    private val recipientResolver: SmsRecipientResolver = mock()
    private val smsLogPersister: SmsLogPersister = mock()

    private val service = SmsSendService(
        aligoClient = aligoClient,
        aligoProperties = aligoProperties,
        churchMemberRepository = churchMemberRepository,
        recipientResolver = recipientResolver,
        smsLogPersister = smsLogPersister,
    )

    private fun makeSmsLog(id: Long = 1L): SmsLog {
        val log = SmsLog.create(
            msgType = SmsMessageType.SMS,
            sender = "01000000000",
            title = null,
            totalCount = 2,
            testMode = false,
            requestedBy = 99L,
        )
        val idField = SmsLog::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(log, id)
        return log
    }

    private fun makeSuccessResult() = AligoSendResult(
        msgId = "MSG-001",
        successCnt = 2,
        errorCnt = 0,
        resultCode = 1,
        message = "success",
    )

    /** Stubs preSaveSend to return the given log regardless of arguments. */
    private fun stubPreSaveSend(log: SmsLog) {
        whenever(
            smsLogPersister.preSaveSend(
                msgType = any(),
                sender = any(),
                title = anyOrNull(),
                testMode = any(),
                requestedBy = any(),
                recipients = any(),
                messageBody = any(),
            )
        ).thenReturn(log)
    }

    /** Stubs preSaveBulk to return the given log regardless of arguments. */
    private fun stubPreSaveBulk(log: SmsLog) {
        whenever(
            smsLogPersister.preSaveBulk(
                msgType = any(),
                sender = any(),
                title = anyOrNull(),
                testMode = any(),
                requestedBy = any(),
                rows = any(),
            )
        ).thenReturn(log)
    }

    @Test
    fun `send - resolves recipients, pre-saves, calls aligoClient, post-updates on success`() {
        val recipients = listOf(
            ResolvedRecipient(phone = "01011111111", name = "홍길동", churchMemberId = 1L),
            ResolvedRecipient(phone = "01022222222", name = "김철수", churchMemberId = 2L),
        )
        val savedLog = makeSmsLog(id = 42L)

        whenever(recipientResolver.resolve(any(), any())).thenReturn(recipients)
        stubPreSaveSend(savedLog)
        whenever(aligoClient.send(any())).thenReturn(makeSuccessResult())

        val cmd = SendCommand(
            body = "안녕하세요",
            msgType = SmsMessageType.SMS,
            title = null,
            sender = null,
            testMode = null,
            scheduledDate = null,
            scheduledTime = null,
            churchMemberIds = listOf(1L, 2L),
            rawRecipients = emptyList(),
            requestedBy = 99L,
        )

        val outcome = service.send(cmd)

        assertThat(outcome.smsLogId).isEqualTo(42L)
        assertThat(outcome.aligoMsgId).isEqualTo("MSG-001")
        assertThat(outcome.successCnt).isEqualTo(2)
        assertThat(outcome.errorCnt).isEqualTo(0)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(aligoClient).send(paramsCaptor.capture())
        val params = paramsCaptor.firstValue
        assertThat(params["receiver"]).isEqualTo("01011111111,01022222222")
        assertThat(params["msg"]).isEqualTo("안녕하세요")
        assertThat(params["sender"]).isEqualTo("01000000000")
        assertThat(params).doesNotContainKey("msg_type") // SMS type should not add msg_type

        verify(smsLogPersister).postUpdateSuccess(
            smsLogId = 42L,
            msgId = "MSG-001",
            successCnt = 2,
            errorCnt = 0,
            resultCode = 1,
            message = "success",
        )
    }

    @Test
    fun `send - preSaveSend is called with correct resolved recipients and message body`() {
        val recipients = listOf(
            ResolvedRecipient(phone = "01011111111", name = "홍길동", churchMemberId = 1L),
        )
        val savedLog = makeSmsLog(id = 3L)

        whenever(recipientResolver.resolve(listOf(1L), emptyList())).thenReturn(recipients)
        stubPreSaveSend(savedLog)
        whenever(aligoClient.send(any())).thenReturn(makeSuccessResult())

        val cmd = SendCommand(
            body = "본문입니다",
            msgType = SmsMessageType.SMS,
            title = null,
            sender = null,
            testMode = false,
            scheduledDate = null,
            scheduledTime = null,
            churchMemberIds = listOf(1L),
            rawRecipients = emptyList(),
            requestedBy = 7L,
        )

        service.send(cmd)

        verify(smsLogPersister).preSaveSend(
            msgType = SmsMessageType.SMS,
            sender = "01000000000",
            title = null,
            testMode = false,
            requestedBy = 7L,
            recipients = recipients,
            messageBody = "본문입니다",
        )
    }

    @Test
    fun `send - adds destination param when body contains name placeholder and names exist`() {
        val recipients = listOf(
            ResolvedRecipient(phone = "01011111111", name = "홍길동", churchMemberId = 1L),
            ResolvedRecipient(phone = "01022222222", name = null, churchMemberId = 2L),
        )
        val savedLog = makeSmsLog(id = 1L)

        whenever(recipientResolver.resolve(any(), any())).thenReturn(recipients)
        stubPreSaveSend(savedLog)
        whenever(aligoClient.send(any())).thenReturn(makeSuccessResult())

        val cmd = SendCommand(
            body = "%고객명% 님께 알립니다",
            msgType = SmsMessageType.SMS,
            title = null,
            sender = null,
            testMode = null,
            scheduledDate = null,
            scheduledTime = null,
            churchMemberIds = listOf(1L, 2L),
            rawRecipients = emptyList(),
            requestedBy = 1L,
        )

        service.send(cmd)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(aligoClient).send(paramsCaptor.capture())
        val destination = paramsCaptor.firstValue["destination"]
        assertThat(destination).isEqualTo("01011111111|홍길동,01022222222")
    }

    @Test
    fun `send - no destination param when body has no name placeholder`() {
        val recipients = listOf(
            ResolvedRecipient(phone = "01011111111", name = "홍길동", churchMemberId = 1L),
        )
        val savedLog = makeSmsLog(id = 1L)

        whenever(recipientResolver.resolve(any(), any())).thenReturn(recipients)
        stubPreSaveSend(savedLog)
        whenever(aligoClient.send(any())).thenReturn(makeSuccessResult())

        val cmd = SendCommand(
            body = "일반 메시지",
            msgType = SmsMessageType.SMS,
            title = null,
            sender = null,
            testMode = null,
            scheduledDate = null,
            scheduledTime = null,
            churchMemberIds = listOf(1L),
            rawRecipients = emptyList(),
            requestedBy = 1L,
        )

        service.send(cmd)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(aligoClient).send(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue).doesNotContainKey("destination")
    }

    @Test
    fun `send - adds msg_type for LMS`() {
        val recipients = listOf(
            ResolvedRecipient(phone = "01011111111", name = null, churchMemberId = null),
        )
        val savedLog = makeSmsLog(id = 1L)

        whenever(recipientResolver.resolve(any(), any())).thenReturn(recipients)
        stubPreSaveSend(savedLog)
        whenever(aligoClient.send(any())).thenReturn(makeSuccessResult())

        val cmd = SendCommand(
            body = "긴 메시지",
            msgType = SmsMessageType.LMS,
            title = "제목",
            sender = null,
            testMode = null,
            scheduledDate = null,
            scheduledTime = null,
            churchMemberIds = emptyList(),
            rawRecipients = listOf(RawRecipient("01011111111", null)),
            requestedBy = 1L,
        )

        service.send(cmd)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(aligoClient).send(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue["msg_type"]).isEqualTo("LMS")
        assertThat(paramsCaptor.firstValue["title"]).isEqualTo("제목")
    }

    @Test
    fun `send - when AligoApiException thrown, calls postUpdateFailed and rethrows`() {
        val recipients = listOf(
            ResolvedRecipient(phone = "01011111111", name = null, churchMemberId = null),
        )
        val savedLog = makeSmsLog(id = 5L)

        whenever(recipientResolver.resolve(any(), any())).thenReturn(recipients)
        stubPreSaveSend(savedLog)
        whenever(aligoClient.send(any())).thenThrow(AligoApiException(-1, "인증 오류"))

        val cmd = SendCommand(
            body = "테스트",
            msgType = SmsMessageType.SMS,
            title = null,
            sender = null,
            testMode = null,
            scheduledDate = null,
            scheduledTime = null,
            churchMemberIds = emptyList(),
            rawRecipients = listOf(RawRecipient("01011111111", null)),
            requestedBy = 1L,
        )

        assertThatThrownBy { service.send(cmd) }
            .isInstanceOf(AligoApiException::class.java)
            .hasMessageContaining("인증 오류")

        verify(smsLogPersister).postUpdateFailed(
            smsLogId = 5L,
            resultCode = -1,
            message = "인증 오류",
        )
        verify(smsLogPersister, never()).postUpdateSuccess(any(), anyOrNull(), any(), any(), any(), any())
    }

    @Test
    fun `send - uses cmd sender when provided, overriding properties default`() {
        val recipients = listOf(ResolvedRecipient(phone = "01011111111", name = null, churchMemberId = null))
        val savedLog = makeSmsLog(id = 1L)

        whenever(recipientResolver.resolve(any(), any())).thenReturn(recipients)
        stubPreSaveSend(savedLog)
        whenever(aligoClient.send(any())).thenReturn(makeSuccessResult())

        val cmd = SendCommand(
            body = "테스트",
            msgType = SmsMessageType.SMS,
            title = null,
            sender = "01099999999",
            testMode = null,
            scheduledDate = null,
            scheduledTime = null,
            churchMemberIds = emptyList(),
            rawRecipients = listOf(RawRecipient("01011111111", null)),
            requestedBy = 1L,
        )

        service.send(cmd)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(aligoClient).send(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue["sender"]).isEqualTo("01099999999")

        verify(smsLogPersister).preSaveSend(
            msgType = SmsMessageType.SMS,
            sender = "01099999999",
            title = null,
            testMode = false,
            requestedBy = 1L,
            recipients = recipients,
            messageBody = "테스트",
        )
    }

    @Test
    fun `sendBulk - builds rec_N and msg_N params correctly`() {
        val savedLog = makeSmsLog(id = 10L)

        whenever(churchMemberRepository.findAllById(any<List<Long>>())).thenReturn(emptyList())
        stubPreSaveBulk(savedLog)
        whenever(aligoClient.sendMass(any())).thenReturn(makeSuccessResult())

        val cmd = BulkSendCommand(
            msgType = SmsMessageType.LMS,
            rows = listOf(
                BulkRow(churchMemberId = null, phone = "01011111111", name = "홍길동", message = "메시지1"),
                BulkRow(churchMemberId = null, phone = "01022222222", name = "김철수", message = "메시지2"),
            ),
            title = null,
            sender = null,
            testMode = null,
            scheduledDate = null,
            scheduledTime = null,
            requestedBy = 99L,
        )

        val outcome = service.sendBulk(cmd)

        assertThat(outcome.smsLogId).isEqualTo(10L)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(aligoClient).sendMass(paramsCaptor.capture())
        val params = paramsCaptor.firstValue
        assertThat(params["cnt"]).isEqualTo("2")
        assertThat(params["rec_1"]).isEqualTo("01011111111")
        assertThat(params["rec_2"]).isEqualTo("01022222222")
        assertThat(params["msg_1"]).isEqualTo("메시지1")
        assertThat(params["msg_2"]).isEqualTo("메시지2")
        assertThat(params["msg_type"]).isEqualTo("LMS")
        assertThat(params["sender"]).isEqualTo("01000000000")
    }

    @Test
    fun `sendBulk - resolves church member IDs via churchMemberRepository`() {
        val savedLog = makeSmsLog(id = 20L)

        val mockMember = mock<org.happyzion.api.member.domain.ChurchMember> {
            on { id } doReturn 7L
            on { phone } doReturn "01077777777"
            on { name } doReturn "박지성"
        }
        whenever(churchMemberRepository.findAllById(listOf(7L))).thenReturn(listOf(mockMember))
        stubPreSaveBulk(savedLog)
        whenever(aligoClient.sendMass(any())).thenReturn(makeSuccessResult())

        val cmd = BulkSendCommand(
            msgType = SmsMessageType.SMS,
            rows = listOf(
                BulkRow(churchMemberId = 7L, phone = null, name = null, message = "교인에게 보내는 메시지"),
            ),
            title = null,
            sender = null,
            testMode = null,
            scheduledDate = null,
            scheduledTime = null,
            requestedBy = 1L,
        )

        service.sendBulk(cmd)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(aligoClient).sendMass(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue["rec_1"]).isEqualTo("01077777777")
        assertThat(paramsCaptor.firstValue["msg_1"]).isEqualTo("교인에게 보내는 메시지")
    }

    @Test
    fun `sendBulk - when AligoApiException, calls postUpdateFailed and rethrows`() {
        val savedLog = makeSmsLog(id = 99L)

        whenever(churchMemberRepository.findAllById(any<List<Long>>())).thenReturn(emptyList())
        stubPreSaveBulk(savedLog)
        whenever(aligoClient.sendMass(any())).thenThrow(AligoApiException(-99, "bulk error"))

        val cmd = BulkSendCommand(
            msgType = SmsMessageType.SMS,
            rows = listOf(BulkRow(null, "01011111111", null, "msg")),
            title = null,
            sender = null,
            testMode = null,
            scheduledDate = null,
            scheduledTime = null,
            requestedBy = 1L,
        )

        assertThatThrownBy { service.sendBulk(cmd) }
            .isInstanceOf(AligoApiException::class.java)

        verify(smsLogPersister).postUpdateFailed(99L, -99, "bulk error")
    }

    @Nested
    inner class PiiHasherSanityChecks {

        @Test
        fun `piiHasher - hash result differs from raw phone input`() {
            val rawPhone = "01011111111"

            // Verify that piiHasher produces a non-empty hash distinct from the raw phone.
            // SmsLogPersister uses piiHasher.hash(normalizer.forStoredPhone(phone)) for storage.
            val hash = piiHasher.hash(normalizer.forStoredPhone(rawPhone))
            assertThat(hash).isNotBlank()
            assertThat(hash).isNotEqualTo(rawPhone)
        }
    }
}
