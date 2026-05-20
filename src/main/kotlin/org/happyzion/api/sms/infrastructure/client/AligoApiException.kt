package org.happyzion.api.sms.infrastructure.client

class AligoApiException(val resultCode: Int, override val message: String) :
    RuntimeException("Aligo API error $resultCode: $message")
