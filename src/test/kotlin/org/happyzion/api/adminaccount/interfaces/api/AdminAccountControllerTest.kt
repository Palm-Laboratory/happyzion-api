package org.happyzion.api.adminaccount.interfaces.api

import org.happyzion.api.adminaccount.application.AdminAccountManagementService
import org.happyzion.api.adminaccount.application.AdminAccountSummary
import org.happyzion.api.adminaccount.domain.AdminAccountRole
import org.happyzion.api.adminaccount.interfaces.dto.AdminAccountCreateRequest
import org.happyzion.api.adminaccount.interfaces.dto.AdminAccountUpdateRequest
import org.happyzion.api.common.config.AdminProperties
import org.happyzion.api.common.error.ForbiddenException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime

class AdminAccountControllerTest {

    private val adminAccountManagementService: AdminAccountManagementService = mock()

    @Test
    fun `get account returns single account when admin key matches`() {
        val controller = AdminAccountController(
            adminAccountManagementService = adminAccountManagementService,
            adminProperties = AdminProperties(syncKey = "secret-key"),
        )
        whenever(adminAccountManagementService.getAccount(1L, 2L)).thenReturn(
            AdminAccountSummary(
                id = 2L,
                username = "admin",
                displayName = "일반 관리자",
                role = AdminAccountRole.ADMIN,
                active = true,
                lastLoginAt = null,
                createdAt = OffsetDateTime.now(),
                updatedAt = OffsetDateTime.now(),
            )
        )

        val response = controller.getAccount("secret-key", 1L, 2L)

        assertThat(response.id).isEqualTo(2L)
        assertThat(response.username).isEqualTo("admin")
    }

    @Test
    fun `update account delegates to management service`() {
        val controller = AdminAccountController(
            adminAccountManagementService = adminAccountManagementService,
            adminProperties = AdminProperties(syncKey = "secret-key"),
        )
        whenever(
            adminAccountManagementService.updateAdminAccount(
                actorId = org.mockito.kotlin.eq(1L),
                accountId = org.mockito.kotlin.eq(2L),
                command = org.mockito.kotlin.any(),
            )
        ).thenReturn(
            AdminAccountSummary(
                id = 2L,
                username = "admin",
                displayName = "수정된 관리자",
                role = AdminAccountRole.ADMIN,
                active = false,
                lastLoginAt = null,
                createdAt = OffsetDateTime.now(),
                updatedAt = OffsetDateTime.now(),
            )
        )

        val response = controller.updateAccount(
            "secret-key",
            1L,
            2L,
            AdminAccountUpdateRequest(
                username = "admin",
                displayName = "수정된 관리자",
                role = AdminAccountRole.ADMIN,
                active = false,
                password = "new-password-123",
            )
        )

        assertThat(response.displayName).isEqualTo("수정된 관리자")
        assertThat(response.active).isFalse()
    }

    @Test
    fun `delete account delegates to management service`() {
        val controller = AdminAccountController(
            adminAccountManagementService = adminAccountManagementService,
            adminProperties = AdminProperties(syncKey = "secret-key"),
        )

        controller.deleteAccount("secret-key", 1L, 2L)

        verify(adminAccountManagementService).deleteAdminAccount(1L, 2L)
    }

    @Test
    fun `get accounts returns list when admin key matches`() {
        val controller = AdminAccountController(
            adminAccountManagementService = adminAccountManagementService,
            adminProperties = AdminProperties(syncKey = "secret-key"),
        )
        whenever(adminAccountManagementService.getAccounts(1L)).thenReturn(
            listOf(
                AdminAccountSummary(
                    id = 1L,
                    username = "super-admin",
                    displayName = "슈퍼 관리자",
                    role = AdminAccountRole.SUPER_ADMIN,
                    active = true,
                    lastLoginAt = null,
                    createdAt = OffsetDateTime.now(),
                    updatedAt = OffsetDateTime.now(),
                )
            )
        )

        val response = controller.getAccounts("secret-key", 1L)

        assertThat(response.accounts).hasSize(1)
        assertThat(response.accounts[0].username).isEqualTo("super-admin")
        assertThat(response.accounts[0].role).isEqualTo(AdminAccountRole.SUPER_ADMIN)
    }

    @Test
    fun `get accounts throws forbidden when admin key mismatches`() {
        val controller = AdminAccountController(
            adminAccountManagementService = adminAccountManagementService,
            adminProperties = AdminProperties(syncKey = "secret-key"),
        )

        assertThrows<ForbiddenException> {
            controller.getAccounts("wrong-key", 1L)
        }
    }

    @Test
    fun `create account delegates to management service`() {
        val controller = AdminAccountController(
            adminAccountManagementService = adminAccountManagementService,
            adminProperties = AdminProperties(syncKey = "secret-key"),
        )
        whenever(
            adminAccountManagementService.createAdminAccount(
                actorId = org.mockito.kotlin.eq(1L),
                command = org.mockito.kotlin.any(),
            )
        ).thenReturn(
            AdminAccountSummary(
                id = 2L,
                username = "new-admin",
                displayName = "새 관리자",
                role = AdminAccountRole.ADMIN,
                active = true,
                lastLoginAt = null,
                createdAt = OffsetDateTime.now(),
                updatedAt = OffsetDateTime.now(),
            )
        )

        val response = controller.createAccount(
            "secret-key",
            1L,
            AdminAccountCreateRequest(
                username = "new-admin",
                displayName = "새 관리자",
                password = "password-123",
            )
        )

        assertThat(response.username).isEqualTo("new-admin")
        val commandCaptor = argumentCaptor<org.happyzion.api.adminaccount.application.CreateAdminAccountCommand>()
        verify(adminAccountManagementService).createAdminAccount(
            actorId = org.mockito.kotlin.eq(1L),
            command = commandCaptor.capture(),
        )
        assertThat(commandCaptor.firstValue.username).isEqualTo("new-admin")
        assertThat(commandCaptor.firstValue.displayName).isEqualTo("새 관리자")
    }
}
