package org.happyzion.api.member.domain

enum class MemberStatus {
    ACTIVE,
    NEW,
    RESTING,
    LONG_ABSENT,
    TRANSFERRED_OUT,
    DECEASED,
    REMOVED,
}

enum class FaithStage {
    SEEKER,
    NEW_COMER,
    SETTLED,
    GROWING,
    DISCIPLE,
    MINISTER,
    LEADER,
}

enum class FamilyRelation {
    SPOUSE,
    PARENT,
    CHILD,
    SIBLING,
    OTHER,
}

enum class AttendanceStatus {
    ATTEND,
    ABSENT,
    EXCUSED,
    ONLINE,
}

enum class Office {
    LAY,
    DEACON_TEMP,
    DEACON,
    GWONSA,
    ELDER,
    ELDER_EMERITUS,
    EVANGELIST,
    PASTOR,
}

enum class MemberEventType {
    REGISTERED,
    STATUS_CHANGED,
    STAGE_CHANGED,
    OFFICE_CHANGED,
    CELL_MOVED,
    SERVICE_ASSIGNED,
    SERVICE_ENDED,
    TRAINING_COMPLETED,
    ADDRESS_CHANGED,
    PHOTO_CHANGED,
    FAMILY_LINKED,
    FAMILY_UNLINKED,
}

enum class MemberSex {
    M,
    F,
}

enum class BirthCalendar {
    SOLAR,
    LUNAR,
}
