package org.happyzion.api.finance.infrastructure.persistence

import org.happyzion.api.finance.domain.FinanceReportLine
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface FinanceReportLineRepository : JpaRepository<FinanceReportLine, Long> {
    fun findAllByReportId(reportId: Long): List<FinanceReportLine>

    @Modifying
    @Query("DELETE FROM FinanceReportLine l WHERE l.reportId = :reportId")
    fun deleteAllByReportId(reportId: Long)

    @Query("""
        select l from FinanceReportLine l
        where l.reportId in :reportIds
        order by l.reportId, l.categoryId
    """)
    fun findAllByReportIdIn(reportIds: List<Long>): List<FinanceReportLine>
}
