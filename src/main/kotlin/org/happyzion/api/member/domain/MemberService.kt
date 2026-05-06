package org.happyzion.api.member.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.OffsetDateTime

@Entity
@Table(name = "member_service")
class MemberService(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "member_id", nullable = false)
    var memberId: Long,
    @Column(nullable = false, length = 120)
    var department: String,
    @Column(length = 120)
    var team: String? = null,
    @Column(nullable = false, length = 120)
    var role: String,
    @Column(name = "started_at", nullable = false)
    var startedAt: LocalDate,
    @Column(name = "ended_at")
    var endedAt: LocalDate? = null,
    @Column(length = 200)
    var schedule: String? = null,
    @Column(length = 500)
    var note: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
