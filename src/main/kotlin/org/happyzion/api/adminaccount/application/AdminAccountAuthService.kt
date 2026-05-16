package org.happyzion.api.adminaccount.application

import org.happyzion.api.common.error.UnauthorizedException
import org.happyzion.api.adminaccount.infrastructure.persistence.AdminAccountRepository
import org.happyzion.api.common.security.AdminJwtService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AdminAccountAuthService(
    private val adminAccountRepository: AdminAccountRepository,
    private val passwordEncoder: PasswordEncoder,
    private val adminJwtService: AdminJwtService,
) {
    fun authenticate(username: String, password: String): AuthenticatedAdminAccount {
        val normalizedUsername = normalizeUsername(username)
        if (normalizedUsername.isBlank() || password.isBlank()) {
            throw UnauthorizedException("아이디 또는 비밀번호가 올바르지 않습니다.")
        }

        val account = adminAccountRepository.findByUsername(normalizedUsername)
            ?: throw UnauthorizedException("아이디 또는 비밀번호가 올바르지 않습니다.")

        if (!account.active || !passwordEncoder.matches(password, account.passwordHash)) {
            throw UnauthorizedException("아이디 또는 비밀번호가 올바르지 않습니다.")
        }

        val accountId = account.id ?: throw IllegalStateException("관리자 계정 ID가 없습니다.")
        return AuthenticatedAdminAccount(
            id = accountId,
            username = account.username,
            displayName = account.displayName,
            role = account.role,
            token = adminJwtService.issueToken(accountId, account.role),
        )
    }

    fun getCurrentAccount(actorId: Long): AuthenticatedAdminAccount {
        val account = adminAccountRepository.findByIdOrNull(actorId)
            ?: throw UnauthorizedException("관리자 인증이 필요합니다.")

        if (!account.active) {
            throw UnauthorizedException("관리자 인증이 필요합니다.")
        }

        val accountId = account.id ?: throw IllegalStateException("관리자 계정 ID가 없습니다.")
        return AuthenticatedAdminAccount(
            id = accountId,
            username = account.username,
            displayName = account.displayName,
            role = account.role,
            token = adminJwtService.issueToken(accountId, account.role),
        )
    }

    private fun normalizeUsername(value: String?): String = value?.trim()?.lowercase() ?: ""
}
