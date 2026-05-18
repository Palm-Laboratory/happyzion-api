package org.happyzion.api.common.security.pii

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PiiKeyRingTest {

    private val key1 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="   // 32B base64
    private val key2 = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBA="

    @Test
    fun `parses single key and exposes activeKeyId`() {
        val props = PiiEncryptionProperties(keys = "v1:$key1", activeKeyId = "v1", hashKey = "h")
        val ring = PiiKeyRing(props)

        assertThat(ring.activeKeyId).isEqualTo("v1")
        assertThat(ring.keyFor("v1")).isNotNull
    }

    @Test
    fun `parses multiple keys`() {
        val props = PiiEncryptionProperties(keys = "v1:$key1,v2:$key2", activeKeyId = "v2", hashKey = "h")
        val ring = PiiKeyRing(props)

        assertThat(ring.activeKeyId).isEqualTo("v2")
        assertThat(ring.keyFor("v1")).isNotNull
        assertThat(ring.keyFor("v2")).isNotNull
    }

    @Test
    fun `fails fast when activeKeyId is not in keys`() {
        val props = PiiEncryptionProperties(keys = "v1:$key1", activeKeyId = "v9", hashKey = "h")

        assertThatThrownBy { PiiKeyRing(props) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("v9")
    }

    @Test
    fun `fails fast when a key entry is malformed`() {
        val props = PiiEncryptionProperties(keys = "bad-entry", activeKeyId = "v1", hashKey = "h")

        assertThatThrownBy { PiiKeyRing(props) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `fails fast when a key is not 32 bytes`() {
        val shortKey = "QUFB"   // base64 of "AAA", 3 bytes
        val props = PiiEncryptionProperties(keys = "v1:$shortKey", activeKeyId = "v1", hashKey = "h")

        assertThatThrownBy { PiiKeyRing(props) }
            .isInstanceOf(IllegalStateException::class.java)
    }
}
