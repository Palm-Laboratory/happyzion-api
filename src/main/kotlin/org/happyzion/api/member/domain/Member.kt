package org.happyzion.api.member.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.OffsetDateTime

@Entity
@Table(name = "member")
class Member(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false, length = 100)
    var name: String,
    @Column(name = "name_en", length = 100)
    var nameEn: String? = null,
    @Column(name = "baptism_name", length = 100)
    var baptismName: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 1, columnDefinition = "varchar(1)")
    var sex: MemberSex,
    @Column(name = "birth_date", nullable = false)
    var birthDate: LocalDate,
    @Enumerated(EnumType.STRING)
    @Column(name = "birth_calendar", nullable = false, length = 10)
    var birthCalendar: BirthCalendar,
    @Column(nullable = false, length = 30)
    var phone: String,
    @Column(name = "emergency_phone", length = 30)
    var emergencyPhone: String? = null,
    @Column(name = "emergency_relation", length = 50)
    var emergencyRelation: String? = null,
    @Column(length = 150)
    var email: String? = null,
    @Column(nullable = false, length = 200)
    var address: String,
    @Column(name = "address_detail", length = 200)
    var addressDetail: String? = null,
    @Column(length = 120)
    var job: String? = null,
    @Column(name = "photo_path", length = 255)
    var photoPath: String? = null,
    @Column(name = "cell_id", length = 60)
    var cellId: String? = null,
    @Column(name = "cell_label", length = 120)
    var cellLabel: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: MemberStatus,
    @Enumerated(EnumType.STRING)
    @Column(name = "faith_stage", nullable = false, length = 32)
    var faithStage: FaithStage,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var office: Office = Office.LAY,
    @Column(name = "office_appointed_at")
    var officeAppointedAt: LocalDate? = null,
    @Column(name = "registered_at", nullable = false)
    var registeredAt: LocalDate,
    @Column(columnDefinition = "text")
    var memo: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
