package org.happyzion.api.common.security.pii

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PiiEncryptorTest {

    private val key1 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val key2 = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBA="

    private fun ringWith(active: String, keys: String) =
        PiiKeyRing(PiiEncryptionProperties(keys = keys, activeKeyId = active, hashKey = "h"))

    @Test
    fun `encrypt decrypt roundtrip`() {
        val enc = PiiEncryptor(ringWith("v1", "v1:$key1"))

        val ciphertext = enc.encrypt("김철수")

        assertThat(ciphertext).startsWith("v1:")
        assertThat(enc.decrypt(ciphertext)).isEqualTo("김철수")
    }

    @Test
    fun `same plaintext yields different ciphertext each time (IV randomness)`() {
        val enc = PiiEncryptor(ringWith("v1", "v1:$key1"))

        val c1 = enc.encrypt("김철수")
        val c2 = enc.encrypt("김철수")

        assertThat(c1).isNotEqualTo(c2)
        assertThat(enc.decrypt(c1)).isEqualTo("김철수")
        assertThat(enc.decrypt(c2)).isEqualTo("김철수")
    }

    @Test
    fun `decrypt can read ciphertext encrypted with old key id`() {
        val encV1 = PiiEncryptor(ringWith("v1", "v1:$key1"))
        val c = encV1.encrypt("hello")

        val encV2 = PiiEncryptor(ringWith("v2", "v1:$key1,v2:$key2"))

        assertThat(encV2.decrypt(c)).isEqualTo("hello")
    }

    @Test
    fun `decrypt rejects tampered ciphertext`() {
        val enc = PiiEncryptor(ringWith("v1", "v1:$key1"))
        val c = enc.encrypt("hello")
        val tampered = c.dropLast(2) + "XX"

        assertThatThrownBy { enc.decrypt(tampered) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `decrypt rejects unknown key id`() {
        val enc = PiiEncryptor(ringWith("v1", "v1:$key1"))

        assertThatThrownBy { enc.decrypt("v9:AAAA") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("v9")
    }
}
