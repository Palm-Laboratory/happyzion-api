package org.happyzion.api.member.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.OffsetDateTime

@Entity
@Table(name = "member_faith")
class MemberFaith(
    @Id
    @Column(name = "member_id")
    val memberId: Long,
    @Column(name = "confess_date")
    var confessDate: LocalDate? = null,
    @Column(name = "learning_date")
    var learningDate: LocalDate? = null,
    @Column(name = "baptism_date")
    var baptismDate: LocalDate? = null,
    @Column(name = "baptism_place", length = 120)
    var baptismPlace: String? = null,
    @Column(name = "baptism_officiant", length = 120)
    var baptismOfficiant: String? = null,
    @Column(name = "confirmation_date")
    var confirmationDate: LocalDate? = null,
    @Column(name = "previous_church", length = 120)
    var previousChurch: String? = null,
    @Column(name = "transferred_in_at")
    var transferredInAt: LocalDate? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
