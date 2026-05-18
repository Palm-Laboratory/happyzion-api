package org.happyzion.api.adminaccount.application

import org.happyzion.api.adminaccount.infrastructure.persistence.AdminAccountRepository
import org.happyzion.api.common.error.ForbiddenException
import org.happyzion.api.common.error.UnauthorizedException
import org.springframework.stereotype.Component

@Component
class AdminAccountGuard(private val adminAccountRepository: AdminAccountRepository) {

    fun verify(actorId: Long) {
        val admin = adminAccountRepository.findById(actorId).orElse(null)
            ?: throw UnauthorizedException("관리자 계정을 찾을 수 없습니다.")
        if (!admin.active) throw ForbiddenException("비활성 관리자입니다.")
    }
}
