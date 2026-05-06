package org.happyzion.api.adminaccount.infrastructure.persistence

import org.happyzion.api.adminaccount.domain.AdminAccount
import org.happyzion.api.adminaccount.domain.AdminAccountRole
import org.springframework.data.jpa.repository.JpaRepository

interface AdminAccountRepository : JpaRepository<AdminAccount, Long> {
    fun findByUsername(username: String): AdminAccount?
    fun existsByRole(role: AdminAccountRole): Boolean
}
