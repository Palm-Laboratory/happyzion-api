package org.happyzion.api.member.domain

import jakarta.persistence.*
import org.happyzion.api.common.security.pii.EncryptedLocalDateConverter
import org.happyzion.api.common.security.pii.EncryptedStringConverter
import java.time.LocalDate
import java.time.OffsetDateTime

@Entity
@Table(name = "church_member_faith")
class ChurchMemberFaith(
    @Id @Column(name = "church_member_id") val churchMemberId: Long,

    @Column(name = "confess_date_enc") @Convert(converter = EncryptedLocalDateConverter::class)
    var confessDate: LocalDate?,
    @Column(name = "learning_date_enc") @Convert(converter = EncryptedLocalDateConverter::class)
    var learningDate: LocalDate?,
    @Column(name = "baptism_date_enc") @Convert(converter = EncryptedLocalDateConverter::class)
    var baptismDate: LocalDate?,
    @Column(name = "baptism_place_enc") @Convert(converter = EncryptedStringConverter::class)
    var baptismPlace: String?,
    @Column(name = "baptism_officiant_enc") @Convert(converter = EncryptedStringConverter::class)
    var baptismOfficiant: String?,
    @Column(name = "confirmation_date_enc") @Convert(converter = EncryptedLocalDateConverter::class)
    var confirmationDate: LocalDate?,
    @Column(name = "previous_church_enc") @Convert(converter = EncryptedStringConverter::class)
    var previousChurch: String?,
    @Column(name = "transferred_in_at_enc") @Convert(converter = EncryptedLocalDateConverter::class)
    var transferredInAt: LocalDate?,
) {
    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
        private set
}
