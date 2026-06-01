package org.happyzion.api.adminaccount.infrastructure.persistence

import org.happyzion.api.adminaccount.domain.AdminAccount
import org.happyzion.api.adminaccount.domain.AdminAccountRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AdminAccountRepository : JpaRepository<AdminAccount, Long> {
    fun findByUsername(username: String): AdminAccount?
    fun existsByRole(role: AdminAccountRole): Boolean

    @Query(
        value = """
            select exists (
                select 1 from menu_revision where created_by = :accountId
                union all
                select 1 from post where author_id = :accountId
                union all
                select 1 from post_asset where uploaded_by_actor_id = :accountId
                union all
                select 1 from upload_token where actor_id = :accountId
                union all
                select 1 from church_member_audit_log where actor_id = :accountId
                union all
                select 1 from sms_log where requested_by = :accountId
            )
        """,
        nativeQuery = true,
    )
    fun hasOperationalReferences(@Param("accountId") accountId: Long): Boolean
}
