package org.happyzion.api.common.security.pii

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class MemberSearchKeyNormalizerTest {

    private val n = MemberSearchKeyNormalizer()

    @Test
    fun `forStoredName strips whitespace and lowercases`() {
        assertThat(n.forStoredName("  김 철 수 ")).isEqualTo("김철수")
        assertThat(n.forStoredName("John Doe")).isEqualTo("johndoe")
    }

    @Test
    fun `forStoredName rejects blank after normalization`() {
        assertThatThrownBy { n.forStoredName("   ") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `forStoredPhone extracts digits and accepts 9+ digits`() {
        assertThat(n.forStoredPhone("010-1234-5678")).isEqualTo("01012345678")
    }

    @Test
    fun `forStoredPhone rejects under 9 digits`() {
        assertThatThrownBy { n.forStoredPhone("12345") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `forPhoneQuery returns Last4 for exactly 4 digits`() {
        assertThat(n.forPhoneQuery("5678")).isEqualTo(PhoneQueryKey.Last4("5678"))
    }

    @Test
    fun `forPhoneQuery returns Full for 9+ digits, with last4 part too`() {
        assertThat(n.forPhoneQuery("010-1234-5678"))
            .isEqualTo(PhoneQueryKey.Full(full = "01012345678", last4 = "5678"))
    }

    @Test
    fun `forPhoneQuery returns null for ambiguous lengths`() {
        assertThat(n.forPhoneQuery("12345")).isNull()
        assertThat(n.forPhoneQuery("")).isNull()
    }

    @Test
    fun `last4 of full phone`() {
        assertThat(n.last4OfStored("01012345678")).isEqualTo("5678")
    }
}
