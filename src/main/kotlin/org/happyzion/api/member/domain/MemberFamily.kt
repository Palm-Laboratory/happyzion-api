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
@Table(name = "member_family")
class MemberFamily(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "member_id", nullable = false)
    var memberId: Long,
    @Column(name = "related_member_id")
    var relatedMemberId: Long? = null,
    @Column(name = "external_name", length = 100)
    var externalName: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var relation: FamilyRelation,
    @Column(name = "relation_detail", length = 50)
    var relationDetail: String? = null,
    @Column(name = "is_head", nullable = false)
    var isHead: Boolean = false,
    @Enumerated(EnumType.STRING)
    @Column(length = 1, columnDefinition = "varchar(1)")
    var sex: MemberSex? = null,
    @Column(length = 30)
    var phone: String? = null,
    @Column(name = "birth_date")
    var birthDate: LocalDate? = null,
    @Column(name = "group_note", length = 200)
    var groupNote: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
