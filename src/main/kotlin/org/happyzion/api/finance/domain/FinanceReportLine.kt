package org.happyzion.api.finance.domain

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "finance_report_line")
class FinanceReportLine(
    @Column(name = "report_id", nullable = false)
    val reportId: Long,

    @Column(name = "category_id", nullable = false)
    val categoryId: Long,

    @Column(nullable = false)
    var amount: Long = 0,
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
}
