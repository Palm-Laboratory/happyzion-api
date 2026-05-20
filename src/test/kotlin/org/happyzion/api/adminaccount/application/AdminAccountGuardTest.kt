package org.happyzion.api.adminaccount.application

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.happyzion.api.adminaccount.domain.AdminAccount
import org.happyzion.api.adminaccount.domain.AdminAccountRole
import org.happyzion.api.adminaccount.infrastructure.persistence.AdminAccountRepository
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.UnauthorizedException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.OffsetDateTime
import java.util.Optional

class AdminAccountGuardTest {

    @Test
    fun `passes when admin is active`() {
        val admin = activeAdmin(id = 1)
        val repo = mock<AdminAccountRepository> { on { findById(1) } doReturn Optional.of(admin) }
        AdminAccountGuard(repo).verify(1)
    }

    @Test
    fun `throws Unauthorized when admin not found`() {
        val repo = mock<AdminAccountRepository> { on { findById(1) } doReturn Optional.empty() }

        assertThatThrownBy { AdminAccountGuard(repo).verify(1) }
            .isInstanceOf(UnauthorizedException::class.java)
    }

    @Test
    fun `throws Forbidden when admin is inactive`() {
        val inactive = AdminAccount(
            id = 1,
            username = "u",
            displayName = "U",
            passwordHash = "x",
            role = AdminAccountRole.ADMIN,
            active = false,
            lastLoginAt = null,
            createdAt = OffsetDateTime.now(),
            updatedAt = OffsetDateTime.now(),
        )
        val repo = mock<AdminAccountRepository> { on { findById(1) } doReturn Optional.of(inactive) }

        assertThatThrownBy { AdminAccountGuard(repo).verify(1) }
            .isInstanceOf(ForbiddenException::class.java)
    }

    private fun activeAdmin(id: Long) = AdminAccount(
        id = id,
        username = "u",
        displayName = "U",
        passwordHash = "x",
        role = AdminAccountRole.ADMIN,
        active = true,
        lastLoginAt = null,
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now(),
    )
}
