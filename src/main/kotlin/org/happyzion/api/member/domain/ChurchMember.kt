package org.happyzion.api.member.domain

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.happyzion.api.common.security.pii.EncryptedLocalDateConverter
import org.happyzion.api.common.security.pii.EncryptedStringConverter
import org.happyzion.api.common.security.pii.MemberSearchKeyNormalizer
import org.happyzion.api.common.security.pii.PiiHasher
import java.time.LocalDate
import java.time.OffsetDateTime

@Entity
@Table(name = "church_member")
class ChurchMember(
    @Column(name = "name_enc", nullable = false)
    @Convert(converter = EncryptedStringConverter::class)
    var name: String,

    @Column(name = "name_hash", nullable = false, length = 64)
    var nameHash: String,

    @Column(name = "phone_enc", nullable = false)
    @Convert(converter = EncryptedStringConverter::class)
    var phone: String,

    @Column(name = "phone_hash", nullable = false, length = 64)
    var phoneHash: String,

    @Column(name = "phone_last4_hash", length = 64)
    var phoneLast4Hash: String?,

    @Column(name = "email_enc")
    @Convert(converter = EncryptedStringConverter::class)
    var email: String?,

    @Column(name = "address_enc", nullable = false)
    @Convert(converter = EncryptedStringConverter::class)
    var address: String,

    @Column(name = "address_detail_enc")
    @Convert(converter = EncryptedStringConverter::class)
    var addressDetail: String?,

    @Column(name = "job_enc")
    @Convert(converter = EncryptedStringConverter::class)
    var job: String?,

    @Column(name = "memo_enc")
    @Convert(converter = EncryptedStringConverter::class)
    var memo: String?,

    @Column(name = "birth_date_enc", nullable = false)
    @Convert(converter = EncryptedLocalDateConverter::class)
    var birthDate: LocalDate,

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 1)
    var sex: Sex,

    @Enumerated(EnumType.STRING) @Column(name = "birth_calendar", nullable = false, length = 10)
    var birthCalendar: BirthCalendar,

    @Column(name = "photo_asset_id")
    var photoAssetId: Long?,

    @Column(name = "cell_label", length = 120)
    var cellLabel: String?,

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    var status: ChurchMemberStatus,

    @Enumerated(EnumType.STRING) @Column(name = "faith_stage", length = 32)
    var faithStage: FaithStage?,

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    var office: ChurchMemberOffice,

    @Column(name = "office_appointed_at")
    var officeAppointedAt: LocalDate?,

    @Column(name = "registered_at", nullable = false)
    var registeredAt: LocalDate,
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        private set

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
        private set

    fun rename(newName: String, hasher: PiiHasher, normalizer: MemberSearchKeyNormalizer) {
        this.name = newName
        this.nameHash = hasher.hash(normalizer.forStoredName(newName))
    }

    fun changePhone(newPhone: String, hasher: PiiHasher, normalizer: MemberSearchKeyNormalizer) {
        val stored = normalizer.forStoredPhone(newPhone)
        this.phone = newPhone
        this.phoneHash = hasher.hash(stored)
        this.phoneLast4Hash = normalizer.last4OfStored(stored)?.let(hasher::hash)
    }

    fun changeAddress(newAddress: String, newDetail: String?) {
        this.address = newAddress
        this.addressDetail = newDetail
    }

    fun changeEmail(newEmail: String?) { this.email = newEmail }
    fun changeJob(newJob: String?) { this.job = newJob }
    fun changeMemo(newMemo: String?) { this.memo = newMemo }
    fun changeBirth(newDate: LocalDate, calendar: BirthCalendar) {
        this.birthDate = newDate; this.birthCalendar = calendar
    }
    fun changeSex(newSex: Sex) { this.sex = newSex }
    fun changeCellLabel(newLabel: String?) { this.cellLabel = newLabel }
    fun changeFaithStage(newStage: FaithStage?) { this.faithStage = newStage }
    fun changeRegisteredAt(newDate: LocalDate) { this.registeredAt = newDate }

    fun changeStatus(newStatus: ChurchMemberStatus) {
        require(newStatus != ChurchMemberStatus.REMOVED) {
            "직접 REMOVED 상태로 변경할 수 없습니다. softDelete 경로를 사용하세요."
        }
        this.status = newStatus
    }
    fun markRemoved() { this.status = ChurchMemberStatus.REMOVED }

    fun appointOffice(newOffice: ChurchMemberOffice, at: LocalDate?) {
        if (at != null) require(!at.isAfter(LocalDate.now())) { "직분 임명일은 미래일 수 없습니다." }
        this.office = newOffice
        this.officeAppointedAt = at
    }

    fun linkPhoto(assetId: Long) { this.photoAssetId = assetId }
    fun unlinkPhoto() { this.photoAssetId = null }

    companion object {
        fun create(
            name: String, phone: String, email: String?,
            birthDate: LocalDate, birthCalendar: BirthCalendar, sex: Sex,
            address: String, addressDetail: String?, job: String?,
            cellLabel: String?, status: ChurchMemberStatus, faithStage: FaithStage?,
            office: ChurchMemberOffice, officeAppointedAt: LocalDate?,
            registeredAt: LocalDate, memo: String?,
            hasher: PiiHasher, normalizer: MemberSearchKeyNormalizer,
        ): ChurchMember {
            require(status != ChurchMemberStatus.REMOVED) { "REMOVED 상태로 신규 생성할 수 없습니다." }
            if (officeAppointedAt != null) require(!officeAppointedAt.isAfter(LocalDate.now())) {
                "직분 임명일은 미래일 수 없습니다."
            }
            val storedPhone = normalizer.forStoredPhone(phone)
            return ChurchMember(
                name = name,
                nameHash = hasher.hash(normalizer.forStoredName(name)),
                phone = phone,
                phoneHash = hasher.hash(storedPhone),
                phoneLast4Hash = normalizer.last4OfStored(storedPhone)?.let(hasher::hash),
                email = email,
                address = address,
                addressDetail = addressDetail,
                job = job,
                memo = memo,
                birthDate = birthDate,
                sex = sex,
                birthCalendar = birthCalendar,
                photoAssetId = null,
                cellLabel = cellLabel,
                status = status,
                faithStage = faithStage,
                office = office,
                officeAppointedAt = officeAppointedAt,
                registeredAt = registeredAt,
            )
        }
    }
}
