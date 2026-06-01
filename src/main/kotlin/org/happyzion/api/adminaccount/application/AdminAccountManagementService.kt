package org.happyzion.api.adminaccount.application

import org.happyzion.api.adminaccount.domain.AdminAccount
import org.happyzion.api.adminaccount.domain.AdminAccountRole
import org.happyzion.api.adminaccount.infrastructure.persistence.AdminAccountRepository
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.NotFoundException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AdminAccountManagementService(
    private val adminAccountRepository: AdminAccountRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    fun getAccount(actorId: Long, accountId: Long): AdminAccountSummary {
        requireSuperAdmin(actorId)

        val account = adminAccountRepository.findByIdOrNull(accountId)
            ?: throw NotFoundException("관리자 계정을 찾을 수 없습니다. id=$accountId")

        return account.toSummary()
    }

    fun getAccounts(actorId: Long): List<AdminAccountSummary> {
        requireSuperAdmin(actorId)

        return adminAccountRepository.findAll()
            .sortedWith(
                compareByDescending<AdminAccount> { it.role == AdminAccountRole.SUPER_ADMIN }
                    .thenBy { it.username }
            )
            .map { it.toSummary() }
    }

    fun createAdminAccount(actorId: Long, command: CreateAdminAccountCommand): AdminAccountSummary {
        requireSuperAdmin(actorId)

        val username = normalizeUsername(command.username)
        val displayName = command.displayName.trim()
        val password = command.password.trim()

        require(username.isNotBlank()) { "관리자 아이디를 입력해 주세요." }
        require(displayName.isNotBlank()) { "관리자 이름을 입력해 주세요." }
        require(password.length >= 8) { "비밀번호는 8자 이상이어야 합니다." }

        if (adminAccountRepository.findByUsername(username) != null) {
            throw IllegalArgumentException("이미 사용 중인 관리자 아이디입니다.")
        }

        val account = adminAccountRepository.save(
            AdminAccount(
                username = username,
                displayName = displayName,
                passwordHash = passwordEncoder.encode(password),
                role = AdminAccountRole.ADMIN,
            )
        )

        return account.toSummary()
    }

    fun updateAdminAccount(
        actorId: Long,
        accountId: Long,
        command: UpdateAdminAccountCommand,
    ): AdminAccountSummary {
        requireSuperAdmin(actorId)

        val account = adminAccountRepository.findByIdOrNull(accountId)
            ?: throw NotFoundException("관리자 계정을 찾을 수 없습니다. id=$accountId")

        val username = normalizeUsername(command.username)
        val displayName = command.displayName.trim()
        require(username.isNotBlank()) { "관리자 아이디를 입력해 주세요." }
        require(displayName.isNotBlank()) { "관리자 이름을 입력해 주세요." }

        val password = command.password?.trim()
        if (password != null && password.isNotEmpty() && password.length < 8) {
            throw IllegalArgumentException("비밀번호는 8자 이상이어야 합니다.")
        }

        if (username != account.username) {
            val existing = adminAccountRepository.findByUsername(username)
            if (existing != null && existing.id != account.id) {
                throw IllegalArgumentException("이미 사용 중인 관리자 아이디입니다.")
            }
        }

        if (actorId == accountId) {
            val hasUnsupportedChange =
                command.role != account.role ||
                    command.active != account.active

            val hasAllowedChange =
                username != account.username ||
                    displayName != account.displayName ||
                    !password.isNullOrEmpty()

            if (hasUnsupportedChange || !hasAllowedChange) {
                throw ForbiddenException("본인 계정은 아이디, 이름, 비밀번호만 변경할 수 있습니다.")
            }
        }

        val updated = adminAccountRepository.save(
            AdminAccount(
                id = account.id,
                username = username,
                displayName = displayName,
                passwordHash = if (password.isNullOrEmpty()) account.passwordHash else passwordEncoder.encode(password),
                role = command.role,
                active = command.active,
                lastLoginAt = account.lastLoginAt,
                createdAt = account.createdAt,
                updatedAt = account.updatedAt,
            )
        )

        return updated.toSummary()
    }

    fun deleteAdminAccount(actorId: Long, accountId: Long) {
        requireSuperAdmin(actorId)

        if (actorId == accountId) {
            throw ForbiddenException("본인 계정은 삭제할 수 없습니다.")
        }

        val account = adminAccountRepository.findByIdOrNull(accountId)
            ?: throw NotFoundException("관리자 계정을 찾을 수 없습니다. id=$accountId")

        if (account.role == AdminAccountRole.SUPER_ADMIN) {
            throw ForbiddenException("슈퍼 관리자 계정은 삭제할 수 없습니다.")
        }

        if (adminAccountRepository.hasOperationalReferences(accountId)) {
            throw IllegalArgumentException("운영 이력이 있는 관리자 계정은 삭제할 수 없습니다. 계정 상태를 비활성으로 변경해 주세요.")
        }

        try {
            adminAccountRepository.delete(account)
            adminAccountRepository.flush()
        } catch (_: DataIntegrityViolationException) {
            throw IllegalArgumentException("운영 이력이 있는 관리자 계정은 삭제할 수 없습니다. 계정 상태를 비활성으로 변경해 주세요.")
        }
    }

    private fun requireSuperAdmin(actorId: Long) {
        val actor = adminAccountRepository.findByIdOrNull(actorId)
            ?: throw NotFoundException("관리자 계정을 찾을 수 없습니다. id=$actorId")

        if (actor.role != AdminAccountRole.SUPER_ADMIN || !actor.active) {
            throw ForbiddenException("슈퍼 관리자만 관리자 계정을 관리할 수 있습니다.")
        }
    }

    private fun AdminAccount.toSummary(): AdminAccountSummary =
        AdminAccountSummary(
            id = id ?: throw IllegalStateException("관리자 계정 ID가 없습니다."),
            username = username,
            displayName = displayName,
            role = role,
            active = active,
            lastLoginAt = lastLoginAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun normalizeUsername(value: String?): String = value?.trim()?.lowercase() ?: ""
}
