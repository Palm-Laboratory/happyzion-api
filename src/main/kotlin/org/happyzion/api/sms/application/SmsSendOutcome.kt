package org.happyzion.api.sms.application

data class SmsSendOutcome(
    val smsLogId: Long,
    val aligoMsgId: String?,
    val successCnt: Int,
    val errorCnt: Int,
)
