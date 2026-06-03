package org.happyzion.api.member.domain
enum class ChurchMemberStatus {
    ACTIVE, NEW, RESTING, LONG_ABSENT, TRANSFERRED_OUT, DECEASED, REMOVED;
    companion object {
        val ACTIVE_SET: Set<ChurchMemberStatus> =
            setOf(ACTIVE)
    }
}
