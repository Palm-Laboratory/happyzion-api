package org.happyzion.api.menu.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class MenuSlugSupportTest {

    @ParameterizedTest
    @MethodSource("slugCases")
    fun `slugifyToAscii handles boundary inputs`(
        rawText: String,
        expected: String,
    ) {
        assertThat(MenuSlugSupport.slugifyToAscii(rawText)).isEqualTo(expected)
    }

    @Test
    fun `slugifyToAscii keeps very long Korean labels deterministic`() {
        assertThat(MenuSlugSupport.slugifyToAscii("가".repeat(80))).isEqualTo("ga".repeat(80))
    }

    companion object {
        @JvmStatic
        fun slugCases(): List<Array<String>> = listOf(
            arrayOf("", ""),
            arrayOf("   ", ""),
            arrayOf("🙏🔥", ""),
            arrayOf("!!!", ""),
            arrayOf("ㄱㄴㄷ", ""),
            arrayOf("Hello   World", "hello-world"),
            arrayOf("Kids & Youth!", "kids-youth"),
            arrayOf("a😀b", "a-b"),
            arrayOf("교회 소개", "gyohoe-sogae"),
            arrayOf("예배 안내", "yebae-annae"),
            arrayOf("한글English123", "hangeulenglish123"),
            arrayOf("중복---구분", "jungbok-gubun"),
            arrayOf("2026 부활절 예배", "2026-buhwaljeol-yebae"),
        )
    }
}
