package org.happyzion.api.sms.application

import org.happyzion.api.member.infrastructure.persistence.ChurchMemberRepository
import org.springframework.stereotype.Component

data class ResolvedRecipient(
    val phone: String,
    val name: String?,
    val churchMemberId: Long?,
)

@Component
class SmsRecipientResolver(
    private val churchMemberRepository: ChurchMemberRepository,
) {

    /**
     * Resolves church member IDs and raw recipients into a deduplicated list of recipients.
     * Dedup is done by normalized phone (digits only), keeping the first occurrence.
     */
    fun resolve(
        churchMemberIds: List<Long>,
        rawRecipients: List<RawRecipient>,
    ): List<ResolvedRecipient> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<ResolvedRecipient>()

        // Load church members
        if (churchMemberIds.isNotEmpty()) {
            val members = churchMemberRepository.findAllById(churchMemberIds)
            for (member in members) {
                val normalizedPhone = member.phone.filter(Char::isDigit)
                if (seen.add(normalizedPhone)) {
                    result.add(
                        ResolvedRecipient(
                            phone = member.phone,
                            name = member.name,
                            churchMemberId = member.id,
                        )
                    )
                }
            }
        }

        // Add raw recipients
        for (raw in rawRecipients) {
            val normalizedPhone = raw.phone.filter(Char::isDigit)
            if (seen.add(normalizedPhone)) {
                result.add(
                    ResolvedRecipient(
                        phone = raw.phone,
                        name = raw.name,
                        churchMemberId = null,
                    )
                )
            }
        }

        return result
    }
}
