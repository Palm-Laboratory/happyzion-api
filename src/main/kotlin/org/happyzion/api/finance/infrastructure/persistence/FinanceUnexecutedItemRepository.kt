package org.happyzion.api.finance.infrastructure.persistence

import org.happyzion.api.finance.domain.FinanceUnexecutedItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface FinanceUnexecutedItemRepository : JpaRepository<FinanceUnexecutedItem, Long> {
    fun findAllByReportIdOrderBySortOrder(reportId: Long): List<FinanceUnexecutedItem>

    @Modifying
    @Query("DELETE FROM FinanceUnexecutedItem i WHERE i.reportId = :reportId")
    fun deleteAllByReportId(reportId: Long)
}
