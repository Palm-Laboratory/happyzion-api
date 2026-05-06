package org.happyzion.api.adminaccount.interfaces.api

import org.happyzion.api.adminaccount.application.AdminAccountAuthService
import org.happyzion.api.adminaccount.application.AuthenticatedAdminAccount
import org.happyzion.api.adminaccount.domain.AdminAccountRole
import org.happyzion.api.adminaccount.interfaces.dto.AdminAccountAuthenticateRequest
import org.happyzion.api.common.config.AdminProperties
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.UnauthorizedException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AdminAuthControllerTest {

    private val adminAccountAuthService: AdminAccountAuthService = mock()

    @Test
    fun `login returns account when credentials are valid`() {
        whenever(adminAccountAuthService.authenticate("super-admin", "password-123")).thenReturn(
            AuthenticatedAdminAccount(
                id = 1L,
                username = "super-admin",
                displayName = "슈퍼 관리자",
                role = AdminAccountRole.SUPER_ADMIN,
            )
        )
        val controller = AdminAuthController(
            adminAccountAuthService,
            AdminProperties(syncKey = "secret-key"),
        )

        val response = controller.login(
            AdminAccountAuthenticateRequest(username = "super-admin", password = "password-123"),
        )

        assertThat(response.username).isEqualTo("super-admin")
        assertThat(response.role).isEqualTo(AdminAccountRole.SUPER_ADMIN)
    }

    @Test
    fun `login throws unauthorized when credentials are invalid`() {
        whenever(adminAccountAuthService.authenticate("super-admin", "wrong-password")).thenThrow(
            UnauthorizedException("아이디 또는 비밀번호가 올바르지 않습니다.")
        )
        val controller = AdminAuthController(
            adminAccountAuthService,
            AdminProperties(syncKey = "secret-key"),
        )

        assertThrows<UnauthorizedException> {
            controller.login(
                AdminAccountAuthenticateRequest(username = "super-admin", password = "wrong-password"),
            )
        }
    }

    @Test
    fun `me returns current authenticated account when admin key matches`() {
        whenever(adminAccountAuthService.getCurrentAccount(1L)).thenReturn(
            AuthenticatedAdminAccount(
                id = 1L,
                username = "happyzion.admin",
                displayName = "총관리자",
                role = AdminAccountRole.SUPER_ADMIN,
            )
        )
        val controller = AdminAuthController(
            adminAccountAuthService,
            AdminProperties(syncKey = "secret-key"),
        )

        val response = controller.me("secret-key", 1L)

        assertThat(response.username).isEqualTo("happyzion.admin")
        assertThat(response.displayName).isEqualTo("총관리자")
    }

    @Test
    fun `me throws forbidden when admin key mismatches`() {
        val controller = AdminAuthController(
            adminAccountAuthService,
            AdminProperties(syncKey = "secret-key"),
        )

        assertThrows<ForbiddenException> {
            controller.me("wrong-key", 1L)
        }
    }
}
