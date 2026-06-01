package org.happyzion.api.adminaccount.application

import org.happyzion.api.adminaccount.domain.AdminAccount
import org.happyzion.api.adminaccount.domain.AdminAccountRole
import org.happyzion.api.adminaccount.infrastructure.persistence.AdminAccountRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.never
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime
import java.util.Optional
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.dao.DataIntegrityViolationException

class AdminAccountManagementServiceTest {

    private val adminAccountRepository: AdminAccountRepository = mock()
    private val passwordEncoder = BCryptPasswordEncoder()
    private val service = AdminAccountManagementService(
        adminAccountRepository = adminAccountRepository,
        passwordEncoder = passwordEncoder,
    )

    @Test
    fun `get account returns single account for super admin actor`() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(
            Optional.of(
                AdminAccount(
                    id = 1L,
                    username = "super-admin",
                    displayName = "슈퍼 관리자",
                    passwordHash = "hash",
                    role = AdminAccountRole.SUPER_ADMIN,
                )
            )
        )
        whenever(adminAccountRepository.findById(2L)).thenReturn(
            Optional.of(
                AdminAccount(
                    id = 2L,
                    username = "admin",
                    displayName = "일반 관리자",
                    passwordHash = "hash",
                    role = AdminAccountRole.ADMIN,
                )
            )
        )

        val account = service.getAccount(actorId = 1L, accountId = 2L)

        assertThat(account.id).isEqualTo(2L)
        assertThat(account.username).isEqualTo("admin")
    }

    @Test
    fun `get accounts returns super admin first`() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(
            Optional.of(
                AdminAccount(
                    id = 1L,
                    username = "super-admin",
                    displayName = "슈퍼 관리자",
                    passwordHash = "hash",
                    role = AdminAccountRole.SUPER_ADMIN,
                )
            )
        )
        whenever(adminAccountRepository.findAll()).thenReturn(
            listOf(
                AdminAccount(
                    id = 2L,
                    username = "admin",
                    displayName = "일반 관리자",
                    passwordHash = "hash",
                    role = AdminAccountRole.ADMIN,
                ),
                AdminAccount(
                    id = 1L,
                    username = "super-admin",
                    displayName = "슈퍼 관리자",
                    passwordHash = "hash",
                    role = AdminAccountRole.SUPER_ADMIN,
                ),
            )
        )

        val accounts = service.getAccounts(actorId = 1L)

        assertThat(accounts.map { it.username }).containsExactly("super-admin", "admin")
    }

    @Test
    fun `update admin account updates target account`() {
        val createdAt = OffsetDateTime.now().minusDays(1)
        whenever(adminAccountRepository.findById(1L)).thenReturn(
            Optional.of(
                AdminAccount(
                    id = 1L,
                    username = "super-admin",
                    displayName = "슈퍼 관리자",
                    passwordHash = "hash",
                    role = AdminAccountRole.SUPER_ADMIN,
                )
            )
        )
        whenever(adminAccountRepository.findById(2L)).thenReturn(
            Optional.of(
                AdminAccount(
                    id = 2L,
                    username = "admin",
                    displayName = "기존 관리자",
                    passwordHash = passwordEncoder.encode("old-password"),
                    role = AdminAccountRole.ADMIN,
                    active = true,
                    createdAt = createdAt,
                )
            )
        )
        whenever(adminAccountRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<AdminAccount>(0)
        }

        val result = service.updateAdminAccount(
            actorId = 1L,
            accountId = 2L,
            command = UpdateAdminAccountCommand(
                username = "admin",
                displayName = "수정된 관리자",
                role = AdminAccountRole.ADMIN,
                active = false,
                password = "new-password-123",
            )
        )

        assertThat(result.displayName).isEqualTo("수정된 관리자")
        assertThat(result.active).isFalse()
    }

    @Test
    fun `update admin account allows self username and profile change`() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(
            Optional.of(
                AdminAccount(
                    id = 1L,
                    username = "super-admin",
                    displayName = "슈퍼 관리자",
                    passwordHash = passwordEncoder.encode("old-password"),
                    role = AdminAccountRole.SUPER_ADMIN,
                )
            )
        )
        whenever(adminAccountRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<AdminAccount>(0)
        }
        whenever(adminAccountRepository.findByUsername("happyzion.admin")).thenReturn(null)

        val result = service.updateAdminAccount(
            actorId = 1L,
            accountId = 1L,
            command = UpdateAdminAccountCommand(
                username = " happyzion.admin ",
                displayName = "총관리자",
                role = AdminAccountRole.SUPER_ADMIN,
                active = true,
                password = "new-password-123",
            )
        )

        assertThat(result.username).isEqualTo("happyzion.admin")
        assertThat(result.displayName).isEqualTo("총관리자")
        verify(adminAccountRepository).save(any())
    }

    @Test
    fun `update admin account rejects self role change`() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(
            Optional.of(
                AdminAccount(
                    id = 1L,
                    username = "super-admin",
                    displayName = "슈퍼 관리자",
                    passwordHash = "hash",
                    role = AdminAccountRole.SUPER_ADMIN,
                )
            )
        )

        val exception = assertThrows<Exception> {
            service.updateAdminAccount(
                actorId = 1L,
                accountId = 1L,
                command = UpdateAdminAccountCommand(
                    username = "super-admin",
                    displayName = "슈퍼 관리자",
                    role = AdminAccountRole.ADMIN,
                    active = true,
                    password = "new-password-123",
                )
            )
        }

        assertThat(exception.message).isEqualTo("본인 계정은 아이디, 이름, 비밀번호만 변경할 수 있습니다.")
    }

    @Test
    fun `update admin account rejects duplicate username`() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(
            Optional.of(
                AdminAccount(
                    id = 1L,
                    username = "super-admin",
                    displayName = "슈퍼 관리자",
                    passwordHash = "hash",
                    role = AdminAccountRole.SUPER_ADMIN,
                )
            )
        )
        whenever(adminAccountRepository.findById(2L)).thenReturn(
            Optional.of(
                AdminAccount(
                    id = 2L,
                    username = "admin",
                    displayName = "일반 관리자",
                    passwordHash = "hash",
                    role = AdminAccountRole.ADMIN,
                )
            )
        )
        whenever(adminAccountRepository.findByUsername("super-admin")).thenReturn(
            AdminAccount(
                id = 1L,
                username = "super-admin",
                displayName = "슈퍼 관리자",
                passwordHash = "hash",
                role = AdminAccountRole.SUPER_ADMIN,
            )
        )

        val exception = assertThrows<IllegalArgumentException> {
            service.updateAdminAccount(
                actorId = 1L,
                accountId = 2L,
                command = UpdateAdminAccountCommand(
                    username = "super-admin",
                    displayName = "일반 관리자",
                    role = AdminAccountRole.ADMIN,
                    active = true,
                )
            )
        }

        assertThat(exception.message).isEqualTo("이미 사용 중인 관리자 아이디입니다.")
    }

    @Test
    fun `delete admin account permanently deletes unused normal admin`() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(
            Optional.of(
                AdminAccount(
                    id = 1L,
                    username = "super-admin",
                    displayName = "슈퍼 관리자",
                    passwordHash = "hash",
                    role = AdminAccountRole.SUPER_ADMIN,
                )
            )
        )
        whenever(adminAccountRepository.findById(2L)).thenReturn(
            Optional.of(
                AdminAccount(
                    id = 2L,
                    username = "admin",
                    displayName = "일반 관리자",
                    passwordHash = "hash",
                    role = AdminAccountRole.ADMIN,
                )
            )
        )

        service.deleteAdminAccount(actorId = 1L, accountId = 2L)

        verify(adminAccountRepository).delete(any())
        verify(adminAccountRepository).flush()
    }

    @Test
    fun `delete admin account rejects account with operational references`() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(
            Optional.of(
                AdminAccount(
                    id = 1L,
                    username = "super-admin",
                    displayName = "슈퍼 관리자",
                    passwordHash = "hash",
                    role = AdminAccountRole.SUPER_ADMIN,
                )
            )
        )
        whenever(adminAccountRepository.findById(2L)).thenReturn(
            Optional.of(
                AdminAccount(
                    id = 2L,
                    username = "admin",
                    displayName = "일반 관리자",
                    passwordHash = "hash",
                    role = AdminAccountRole.ADMIN,
                )
            )
        )
        whenever(adminAccountRepository.hasOperationalReferences(2L)).thenReturn(true)

        val exception = assertThrows<Exception> {
            service.deleteAdminAccount(actorId = 1L, accountId = 2L)
        }

        assertThat(exception.message).isEqualTo(
            "운영 이력이 있는 관리자 계정은 삭제할 수 없습니다. 계정 상태를 비활성으로 변경해 주세요."
        )
        verify(adminAccountRepository, never()).delete(any())
    }

    @Test
    fun `delete admin account reports operational references created during deletion`() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(
            Optional.of(
                AdminAccount(
                    id = 1L,
                    username = "super-admin",
                    displayName = "슈퍼 관리자",
                    passwordHash = "hash",
                    role = AdminAccountRole.SUPER_ADMIN,
                )
            )
        )
        whenever(adminAccountRepository.findById(2L)).thenReturn(
            Optional.of(
                AdminAccount(
                    id = 2L,
                    username = "admin",
                    displayName = "일반 관리자",
                    passwordHash = "hash",
                    role = AdminAccountRole.ADMIN,
                )
            )
        )
        doThrow(DataIntegrityViolationException("constraint")).whenever(adminAccountRepository).flush()

        val exception = assertThrows<Exception> {
            service.deleteAdminAccount(actorId = 1L, accountId = 2L)
        }

        assertThat(exception.message).isEqualTo(
            "운영 이력이 있는 관리자 계정은 삭제할 수 없습니다. 계정 상태를 비활성으로 변경해 주세요."
        )
    }

    @Test
    fun `delete admin account rejects self delete`() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(
            Optional.of(
                AdminAccount(
                    id = 1L,
                    username = "super-admin",
                    displayName = "슈퍼 관리자",
                    passwordHash = "hash",
                    role = AdminAccountRole.SUPER_ADMIN,
                )
            )
        )

        val exception = assertThrows<Exception> {
            service.deleteAdminAccount(actorId = 1L, accountId = 1L)
        }

        assertThat(exception.message).isEqualTo("본인 계정은 삭제할 수 없습니다.")
        verify(adminAccountRepository, never()).delete(any())
    }

    @Test
    fun `delete admin account rejects deleting super admin`() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(
            Optional.of(
                AdminAccount(
                    id = 1L,
                    username = "super-admin",
                    displayName = "슈퍼 관리자",
                    passwordHash = "hash",
                    role = AdminAccountRole.SUPER_ADMIN,
                )
            )
        )
        whenever(adminAccountRepository.findById(3L)).thenReturn(
            Optional.of(
                AdminAccount(
                    id = 3L,
                    username = "another-super",
                    displayName = "다른 슈퍼 관리자",
                    passwordHash = "hash",
                    role = AdminAccountRole.SUPER_ADMIN,
                )
            )
        )

        val exception = assertThrows<Exception> {
            service.deleteAdminAccount(actorId = 1L, accountId = 3L)
        }

        assertThat(exception.message).isEqualTo("슈퍼 관리자 계정은 삭제할 수 없습니다.")
        verify(adminAccountRepository, never()).delete(any())
    }

    @Test
    fun `create admin account stores normalized username and encoded password`() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(
            Optional.of(
                AdminAccount(
                    id = 1L,
                    username = "super-admin",
                    displayName = "슈퍼 관리자",
                    passwordHash = "hash",
                    role = AdminAccountRole.SUPER_ADMIN,
                )
            )
        )
        whenever(adminAccountRepository.findByUsername("new-admin")).thenReturn(null)
        whenever(adminAccountRepository.save(any())).thenAnswer { invocation ->
            val account = invocation.arguments[0] as AdminAccount
            AdminAccount(
                id = 3L,
                username = account.username,
                displayName = account.displayName,
                passwordHash = account.passwordHash,
                role = account.role,
                active = account.active,
                lastLoginAt = account.lastLoginAt,
                createdAt = account.createdAt,
                updatedAt = account.updatedAt,
            )
        }

        val result = service.createAdminAccount(
            actorId = 1L,
            CreateAdminAccountCommand(
                username = " New-Admin ",
                displayName = "새 관리자",
                password = "password-123",
            )
        )

        assertThat(result.username).isEqualTo("new-admin")
        assertThat(result.role).isEqualTo(AdminAccountRole.ADMIN)
        verify(adminAccountRepository).save(any())
    }

    @Test
    fun `create admin account rejects duplicate username`() {
        whenever(adminAccountRepository.findById(1L)).thenReturn(
            Optional.of(
                AdminAccount(
                    id = 1L,
                    username = "super-admin",
                    displayName = "슈퍼 관리자",
                    passwordHash = "hash",
                    role = AdminAccountRole.SUPER_ADMIN,
                )
            )
        )
        whenever(adminAccountRepository.findByUsername("new-admin")).thenReturn(
            AdminAccount(
                id = 1L,
                username = "new-admin",
                displayName = "기존 관리자",
                passwordHash = "hash",
                role = AdminAccountRole.ADMIN,
            )
        )

        val exception = assertThrows<IllegalArgumentException> {
            service.createAdminAccount(
                actorId = 1L,
                CreateAdminAccountCommand(
                    username = "new-admin",
                    displayName = "새 관리자",
                    password = "password-123",
                )
            )
        }

        assertThat(exception.message).isEqualTo("이미 사용 중인 관리자 아이디입니다.")
    }

    @Test
    fun `create admin account rejects non super admin actor`() {
        whenever(adminAccountRepository.findById(2L)).thenReturn(
            Optional.of(
                AdminAccount(
                    id = 2L,
                    username = "admin",
                    displayName = "일반 관리자",
                    passwordHash = "hash",
                    role = AdminAccountRole.ADMIN,
                )
            )
        )

        val exception = assertThrows<Exception> {
            service.createAdminAccount(
                actorId = 2L,
                CreateAdminAccountCommand(
                    username = "new-admin",
                    displayName = "새 관리자",
                    password = "password-123",
                )
            )
        }

        assertThat(exception.message).isEqualTo("슈퍼 관리자만 관리자 계정을 관리할 수 있습니다.")
    }
}
