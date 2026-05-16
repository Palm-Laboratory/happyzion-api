package org.happyzion.api.adminaccount.application

import org.happyzion.api.adminaccount.domain.AdminAccountRole
import java.time.OffsetDateTime

data class AuthenticatedAdminAccount(
    val id: Long,
    val username: String,
    val displayName: String,
    val role: AdminAccountRole,
    val token: String,
)

data class AdminAccountSummary(
    val id: Long,
    val username: String,
    val displayName: String,
    val role: AdminAccountRole,
    val active: Boolean,
    val lastLoginAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class CreateAdminAccountCommand(
    val username: String,
    val displayName: String,
    val password: String,
)

data class UpdateAdminAccountCommand(
    val username: String,
    val displayName: String,
    val role: AdminAccountRole,
    val active: Boolean,
    val password: String? = null,
)
