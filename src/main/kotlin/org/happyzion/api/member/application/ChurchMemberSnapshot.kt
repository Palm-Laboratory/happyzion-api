package org.happyzion.api.member.application

import org.happyzion.api.member.domain.*
import java.time.LocalDate

data class ChurchMemberSnapshot(
    val name: String,
    val phone: String,
    val email: String?,
    val birthDate: LocalDate,
    val birthCalendar: BirthCalendar,
    val sex: Sex,
    val address: String,
    val addressDetail: String?,
    val job: String?,
    val memo: String?,
    val photoAssetId: Long?,
    val cellLabel: String?,
    val status: ChurchMemberStatus,
    val faithStage: FaithStage?,
    val office: ChurchMemberOffice,
    val officeAppointedAt: LocalDate?,
    val registeredAt: LocalDate,
) {
    companion object {
        fun of(m: ChurchMember) = ChurchMemberSnapshot(
            name = m.name, phone = m.phone, email = m.email,
            birthDate = m.birthDate, birthCalendar = m.birthCalendar, sex = m.sex,
            address = m.address, addressDetail = m.addressDetail, job = m.job,
            memo = m.memo, photoAssetId = m.photoAssetId, cellLabel = m.cellLabel,
            status = m.status, faithStage = m.faithStage,
            office = m.office, officeAppointedAt = m.officeAppointedAt,
            registeredAt = m.registeredAt,
        )
    }
}
