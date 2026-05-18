package org.happyzion.api.common.security.pii

import org.springframework.stereotype.Component
import java.text.Normalizer

sealed class PhoneQueryKey {
    data class Last4(val last4: String) : PhoneQueryKey()
    data class Full(val full: String, val last4: String) : PhoneQueryKey()
}

@Component
class MemberSearchKeyNormalizer {

    fun forStoredName(raw: String): String {
        val out = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .replace(WHITESPACE, "")
            .lowercase()
        require(out.isNotEmpty()) { "이름은 비어 있을 수 없습니다." }
        return out
    }

    fun forNameQuery(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val out = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .replace(WHITESPACE, "")
            .lowercase()
        return out.ifEmpty { null }
    }

    fun forStoredPhone(raw: String): String {
        val digits = raw.filter(Char::isDigit)
        require(digits.length >= 9) { "전화번호는 9자리 이상이어야 합니다." }
        return digits
    }

    fun forPhoneQuery(raw: String?): PhoneQueryKey? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.filter(Char::isDigit)
        return when {
            digits.length == 4 -> PhoneQueryKey.Last4(digits)
            digits.length >= 9 -> PhoneQueryKey.Full(full = digits, last4 = digits.takeLast(4))
            else -> null
        }
    }

    fun last4OfStored(storedPhone: String): String? =
        if (storedPhone.length >= 8) storedPhone.takeLast(4) else null

    companion object {
        private val WHITESPACE = Regex("[\\p{Z}\\s]+")
    }
}
