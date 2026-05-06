package org.happyzion.api.adminaccount.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "admin_account")
class AdminAccount(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false, unique = true, length = 50)
    val username: String,
    @Column(name = "display_name", nullable = false, length = 100)
    val displayName: String,
    @Column(name = "password_hash", nullable = false, length = 255)
    val passwordHash: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val role: AdminAccountRole,
    @Column(nullable = false)
    val active: Boolean = true,
    @Column(name = "last_login_at")
    val lastLoginAt: OffsetDateTime? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
