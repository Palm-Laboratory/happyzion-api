package org.happyzion.api.common.security.pii

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

@Component
class PiiEncryptor(private val keyRing: PiiKeyRing) {

    private val secureRandom = SecureRandom()

    fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_LENGTH).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, keyRing.activeKey(), GCMParameterSpec(TAG_BIT_LENGTH, iv))
        }
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val payload = iv + ciphertext
        return "${keyRing.activeKeyId}:${Base64.getEncoder().encodeToString(payload)}"
    }

    fun decrypt(token: String): String {
        val sep = token.indexOf(':')
        check(sep > 0) { "암호문 포맷이 올바르지 않습니다." }
        val keyId = token.substring(0, sep)
        val key = keyRing.keyFor(keyId)
            ?: throw IllegalStateException("알 수 없는 키 ID: $keyId")
        val payload = Base64.getDecoder().decode(token.substring(sep + 1))
        check(payload.size > IV_LENGTH) { "암호문 길이가 올바르지 않습니다." }
        val iv = payload.copyOfRange(0, IV_LENGTH)
        val ct = payload.copyOfRange(IV_LENGTH, payload.size)

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BIT_LENGTH, iv))
            }
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (ex: Exception) {
            throw IllegalStateException("암호문 복호에 실패했습니다.", ex)
        }
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_BIT_LENGTH = 128
    }
}
