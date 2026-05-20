package org.happyzion.api.sms.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "sms_log")
class SmsLog(
    @Enumerated(EnumType.STRING)
    @Column(name = "msg_type", nullable = false, length = 8)
    var msgType: SmsMessageType,

    @Column(name = "sender", nullable = false, length = 20)
    var sender: String,

    @Column(name = "title", length = 80)
    var title: String?,

    @Column(name = "total_count", nullable = false)
    var totalCount: Int,

    @Column(name = "success_count", nullable = false)
    var successCount: Int = 0,

    @Column(name = "error_count", nullable = false)
    var errorCount: Int = 0,

    @Column(name = "test_mode", nullable = false)
    var testMode: Boolean = false,

    @Column(name = "requested_by", nullable = false)
    var requestedBy: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        private set

    @Column(name = "requested_at", nullable = false)
    val requestedAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "aligo_msg_id", length = 64)
    var aligoMsgId: String? = null

    @Column(name = "aligo_result_code")
    var aligoResultCode: Int? = null

    @Column(name = "aligo_message")
    var aligoMessage: String? = null

    fun updateAfterSend(msgId: String?, successCnt: Int, errorCnt: Int, resultCode: Int, message: String) {
        this.aligoMsgId = msgId
        this.successCount = successCnt
        this.errorCount = errorCnt
        this.aligoResultCode = resultCode
        this.aligoMessage = message
    }

    companion object {
        fun create(
            msgType: SmsMessageType,
            sender: String,
            title: String?,
            totalCount: Int,
            testMode: Boolean,
            requestedBy: Long,
        ): SmsLog = SmsLog(
            msgType = msgType,
            sender = sender,
            title = title,
            totalCount = totalCount,
            testMode = testMode,
            requestedBy = requestedBy,
        )
    }
}
