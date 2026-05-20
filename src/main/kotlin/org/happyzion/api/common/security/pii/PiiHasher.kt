package org.happyzion.api.common.security.pii

import org.springframework.stereotype.Component
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class PiiHasher(properties: PiiEncryptionProperties) {

    private val key = SecretKeySpec(Base64.getDecoder().decode(properties.hashKey), "HmacSHA256").also {
        check(it.encoded.size == 32) { "HMAC 키는 32바이트여야 합니다." }
    }

    fun hash(input: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(key) }
        val out = mac.doFinal(input.toByteArray(Charsets.UTF_8))
        return out.joinToString("") { "%02x".format(it) }
    }
}
