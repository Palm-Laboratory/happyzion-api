package org.happyzion.api.common.security.pii

import org.springframework.stereotype.Component
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Component
class PiiKeyRing(properties: PiiEncryptionProperties) {

    val activeKeyId: String = properties.activeKeyId
    private val keys: Map<String, SecretKey>

    init {
        val parsed = properties.keys.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .associate { entry ->
                val parts = entry.split(":", limit = 2)
                check(parts.size == 2 && parts[0].isNotEmpty()) {
                    "암호화 키 항목 형식이 올바르지 않습니다: $entry"
                }
                val bytes = Base64.getDecoder().decode(parts[1])
                check(bytes.size == 32) { "AES-256 키는 32바이트여야 합니다 (id=${parts[0]})" }
                parts[0] to SecretKeySpec(bytes, "AES") as SecretKey
            }

        check(parsed.containsKey(activeKeyId)) {
            "active-key-id '$activeKeyId'가 KEYS 목록에 없습니다."
        }
        keys = parsed
    }

    fun keyFor(keyId: String): SecretKey? = keys[keyId]
    fun activeKey(): SecretKey = keys.getValue(activeKeyId)
}
