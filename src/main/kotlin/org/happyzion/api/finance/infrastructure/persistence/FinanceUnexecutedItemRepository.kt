package org.happyzion.api.finance.infrastructure.persistence

import org.happyzion.api.finance.domain.FinanceUnexecutedItem
import org.springframework.data.jpa.repository.JpaRepository

interface FinanceUnexecutedItemRepository : JpaRepository<FinanceUnexecutedItem, Long> {
    fun findAllByReportIdOrderBySortOrder(reportId: Long): List<FinanceUnexecutedItem>
    fun deleteAllByReportId(reportId: Long)
}
