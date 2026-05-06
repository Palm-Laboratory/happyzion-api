package org.happyzion.api.menu.application

object MenuSlugSupport {
    fun slugifyToAscii(rawText: String): String {
        val builder = StringBuilder()
        var pendingSeparator = false

        fun flushSeparator() {
            if (pendingSeparator && builder.isNotEmpty()) {
                builder.append('-')
            }
            pendingSeparator = false
        }

        rawText.trim().forEach { ch ->
            when {
                ch.isAsciiLetter() || ch.isDigit() -> {
                    flushSeparator()
                    builder.append(ch.lowercaseChar())
                }

                ch in '\uAC00'..'\uD7A3' -> {
                    val romanized = romanizeHangulSyllable(ch)
                    if (romanized.isNotBlank()) {
                        flushSeparator()
                        builder.append(romanized)
                    }
                }

                else -> pendingSeparator = builder.isNotEmpty()
            }
        }

        return builder.toString().trim('-')
    }

    private fun romanizeHangulSyllable(ch: Char): String {
        val syllableIndex = ch.code - HANGUL_BASE_CODE
        val choseongIndex = syllableIndex / HANGUL_CHOSEONG_INTERVAL
        val jungseongIndex = (syllableIndex % HANGUL_CHOSEONG_INTERVAL) / HANGUL_JONGSEONG_COUNT
        val jongseongIndex = syllableIndex % HANGUL_JONGSEONG_COUNT

        return buildString {
            append(HANGUL_INITIAL_ROMANIZATION[choseongIndex])
            append(HANGUL_VOWEL_ROMANIZATION[jungseongIndex])
            append(HANGUL_FINAL_ROMANIZATION[jongseongIndex])
        }
    }

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

    private const val HANGUL_BASE_CODE = 0xAC00
    private const val HANGUL_CHOSEONG_INTERVAL = 588
    private const val HANGUL_JONGSEONG_COUNT = 28

    private val HANGUL_INITIAL_ROMANIZATION = arrayOf(
        "g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s",
        "ss", "", "j", "jj", "ch", "k", "t", "p", "h",
    )

    private val HANGUL_VOWEL_ROMANIZATION = arrayOf(
        "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa",
        "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i",
    )

    private val HANGUL_FINAL_ROMANIZATION = arrayOf(
        "", "k", "k", "ks", "n", "nj", "nh", "t", "l", "lk",
        "lm", "lb", "ls", "lt", "lp", "lh", "m", "p", "ps", "t",
        "t", "ng", "t", "t", "k", "t", "p", "h",
    )
}
