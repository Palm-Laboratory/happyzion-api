package org.happyzion.api.adminaccount.application

import org.happyzion.api.adminaccount.domain.AdminAccount
import org.happyzion.api.adminaccount.domain.AdminAccountRole
import org.happyzion.api.adminaccount.infrastructure.persistence.AdminAccountRepository
import org.happyzion.api.common.error.UnauthorizedException
import org.happyzion.api.common.security.AdminJwtService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class AdminAccountAuthServiceTest {

    private val adminAccountRepository: AdminAccountRepository = mock()
    private val passwordEncoder = BCryptPasswordEncoder()
    private val adminJwtService: AdminJwtService = mock()
    private val service = AdminAccountAuthService(
        adminAccountRepository = adminAccountRepository,
        passwordEncoder = passwordEncoder,
        adminJwtService = adminJwtService,
    )

    @Test
    fun `authenticate returns account when credentials match`() {
        whenever(adminAccountRepository.findByUsername("super-admin")).thenReturn(
            AdminAccount(
                id = 1L,
                username = "super-admin",
                displayName = "슈퍼 관리자",
                passwordHash = passwordEncoder.encode("password-123"),
                role = AdminAccountRole.SUPER_ADMIN,
            )
        )
        whenever(adminJwtService.issueToken(1L, AdminAccountRole.SUPER_ADMIN)).thenReturn("token")

        val result = service.authenticate("  SUPER-ADMIN  ", "password-123")

        assertThat(result.id).isEqualTo(1L)
        assertThat(result.username).isEqualTo("super-admin")
        assertThat(result.role).isEqualTo(AdminAccountRole.SUPER_ADMIN)
    }

    @Test
    fun `authenticate throws unauthorized when password mismatches`() {
        whenever(adminAccountRepository.findByUsername("admin")).thenReturn(
            AdminAccount(
                id = 2L,
                username = "admin",
                displayName = "운영자",
                passwordHash = passwordEncoder.encode("password-123"),
                role = AdminAccountRole.ADMIN,
            )
        )

        val exception = assertThrows<UnauthorizedException> {
            service.authenticate("admin", "wrong-password")
        }

        assertThat(exception.message).isEqualTo("아이디 또는 비밀번호가 올바르지 않습니다.")
    }

    @Test
    fun `authenticate does not require bootstrap runtime setup when accounts already exist`() {
        whenever(adminAccountRepository.findByUsername("super-admin")).thenReturn(
            AdminAccount(
                id = 1L,
                username = "super-admin",
                displayName = "슈퍼 관리자",
                passwordHash = passwordEncoder.encode("password-123"),
                role = AdminAccountRole.SUPER_ADMIN,
            )
        )
        whenever(adminJwtService.issueToken(1L, AdminAccountRole.SUPER_ADMIN)).thenReturn("token")

        service.authenticate("super-admin", "password-123")

        org.mockito.kotlin.verify(adminAccountRepository).findByUsername("super-admin")
    }

    @Test
    fun `authenticate throws unauthorized when account is inactive`() {
        whenever(adminAccountRepository.findByUsername("admin")).thenReturn(
            AdminAccount(
                id = 2L,
                username = "admin",
                displayName = "운영자",
                passwordHash = passwordEncoder.encode("password-123"),
                role = AdminAccountRole.ADMIN,
                active = false,
            )
        )

        assertThrows<UnauthorizedException> {
            service.authenticate("admin", "password-123")
        }

        org.mockito.kotlin.verify(adminAccountRepository).findByUsername("admin")
    }

    @Test
    fun `get current account returns latest profile`() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(
            java.util.Optional.of(
                AdminAccount(
                    id = 1L,
                    username = "happyzion.admin",
                    displayName = "총관리자",
                    passwordHash = passwordEncoder.encode("password-123"),
                    role = AdminAccountRole.SUPER_ADMIN,
                )
            )
        )
        whenever(adminJwtService.issueToken(1L, AdminAccountRole.SUPER_ADMIN)).thenReturn("token")

        val result = service.getCurrentAccount(1L)

        assertThat(result.username).isEqualTo("happyzion.admin")
        assertThat(result.displayName).isEqualTo("총관리자")
    }
}
