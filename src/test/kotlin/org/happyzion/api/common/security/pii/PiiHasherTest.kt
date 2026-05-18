package org.happyzion.api.common.security.pii

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PiiHasherTest {

    private val hashKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

    private fun hasher() = PiiHasher(
        PiiEncryptionProperties(keys = "v1:$hashKey", activeKeyId = "v1", hashKey = hashKey)
    )

    @Test
    fun `same input produces same hash`() {
        val h = hasher()
        assertThat(h.hash("김철수")).isEqualTo(h.hash("김철수"))
    }

    @Test
    fun `different input produces different hash`() {
        val h = hasher()
        assertThat(h.hash("김철수")).isNotEqualTo(h.hash("김영희"))
    }

    @Test
    fun `produces 64 hex char output`() {
        val h = hasher().hash("test")
        assertThat(h).matches("[0-9a-f]{64}")
    }
}
