package org.happyzion.api.finance.domain

import jakarta.persistence.*
import java.time.LocalDate
import java.time.OffsetDateTime

@Entity
@Table(name = "finance_unexecuted_item")
class FinanceUnexecutedItem(
    @Column(name = "report_id", nullable = false)
    val reportId: Long,

    @Column(nullable = false, length = 200)
    val content: String,

    @Column(nullable = false)
    val amount: Long = 0,

    @Column(name = "executed_date")
    val executedDate: LocalDate? = null,

    @Column(length = 200)
    val note: String? = null,

    @Column(name = "sort_order", nullable = false)
    val sortOrder: Int = 0,
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
}
