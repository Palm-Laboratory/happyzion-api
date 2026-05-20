package org.happyzion.api.sms.domain

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.happyzion.api.common.security.pii.EncryptedStringConverter

enum class SmsRecipientStatus { PENDING, SENT, FAILED, UNKNOWN }

@Entity
@Table(name = "sms_log_recipient")
class SmsLogRecipient(
    @Column(name = "sms_log_id", nullable = false)
    var smsLogId: Long,

    @Column(name = "phone_enc", nullable = false)
    @Convert(converter = EncryptedStringConverter::class)
    var phoneEnc: String,

    @Column(name = "phone_hash", nullable = false, length = 64)
    var phoneHash: String,

    @Column(name = "receiver_name_enc")
    @Convert(converter = EncryptedStringConverter::class)
    var receiverNameEnc: String?,

    @Column(name = "message_enc", nullable = false)
    @Convert(converter = EncryptedStringConverter::class)
    var messageEnc: String,

    @Column(name = "church_member_id")
    var churchMemberId: Long?,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: SmsRecipientStatus = SmsRecipientStatus.PENDING,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        private set

    @Column(name = "aligo_send_state", length = 40)
    var aligoSendState: String? = null

    fun markSent() { this.status = SmsRecipientStatus.SENT }
    fun markFailed() { this.status = SmsRecipientStatus.FAILED }
    fun markUnknown() { this.status = SmsRecipientStatus.UNKNOWN }

    fun updateAligoState(state: String) { this.aligoSendState = state }
}
