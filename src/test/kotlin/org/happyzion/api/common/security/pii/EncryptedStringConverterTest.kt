package org.happyzion.api.common.security.pii

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EncryptedStringConverterTest {

    private val key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val encryptor = PiiEncryptor(
        PiiKeyRing(PiiEncryptionProperties(keys = "v1:$key", activeKeyId = "v1", hashKey = key))
    )

    @Test
    fun `null plaintext converts to null column value`() {
        val c = EncryptedStringConverter(encryptor)

        assertThat(c.convertToDatabaseColumn(null)).isNull()
        assertThat(c.convertToEntityAttribute(null)).isNull()
    }

    @Test
    fun `roundtrip non-null`() {
        val c = EncryptedStringConverter(encryptor)

        val db = c.convertToDatabaseColumn("김철수")
        assertThat(db).startsWith("v1:")
        assertThat(c.convertToEntityAttribute(db)).isEqualTo("김철수")
    }

    @Test
    fun `empty string roundtrips as empty`() {
        val c = EncryptedStringConverter(encryptor)

        val db = c.convertToDatabaseColumn("")
        assertThat(db).startsWith("v1:")
        assertThat(c.convertToEntityAttribute(db)).isEqualTo("")
    }
}
