package org.happyzion.api.adminaccount.interfaces.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.happyzion.api.adminaccount.application.AdminAccountSummary
import org.happyzion.api.adminaccount.application.AuthenticatedAdminAccount
import org.happyzion.api.adminaccount.domain.AdminAccountRole
import java.time.OffsetDateTime

data class AdminAccountAuthenticateRequest(
    @field:NotBlank(message = "username must not be blank")
    val username: String,
    @field:NotBlank(message = "password must not be blank")
    val password: String,
)

data class AdminAccountCreateRequest(
    @field:NotBlank(message = "username must not be blank")
    val username: String,
    @field:NotBlank(message = "displayName must not be blank")
    val displayName: String,
    @field:NotBlank(message = "password must not be blank")
    @field:Size(min = 8, message = "password must be at least 8 characters")
    val password: String,
)

data class AdminAccountUpdateRequest(
    @field:NotBlank(message = "username must not be blank")
    val username: String,
    @field:NotBlank(message = "displayName must not be blank")
    val displayName: String,
    val role: AdminAccountRole,
    val active: Boolean,
    @field:Size(min = 8, message = "password must be at least 8 characters")
    val password: String? = null,
)

data class AdminAccountDto(
    val id: Long,
    val username: String,
    val displayName: String,
    val role: AdminAccountRole,
    val active: Boolean,
    val lastLoginAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class AdminAuthenticatedAccountDto(
    val id: Long,
    val username: String,
    val displayName: String,
    val role: AdminAccountRole,
    val token: String,
)

data class AdminAccountsResponse(
    val accounts: List<AdminAccountDto>,
)

fun AuthenticatedAdminAccount.toDto(): AdminAuthenticatedAccountDto =
    AdminAuthenticatedAccountDto(
        id = id,
        username = username,
        displayName = displayName,
        role = role,
        token = token,
    )

fun AdminAccountSummary.toDto(): AdminAccountDto =
    AdminAccountDto(
        id = id,
        username = username,
        displayName = displayName,
        role = role,
        active = active,
        lastLoginAt = lastLoginAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
