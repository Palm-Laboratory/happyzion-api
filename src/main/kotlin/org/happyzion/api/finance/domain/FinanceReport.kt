package org.happyzion.api.finance.domain

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "finance_report")
class FinanceReport(
    @Column(nullable = false)
    val year: Int,

    @Column(nullable = false)
    val month: Int,

    @Column(nullable = false)
    val week: Int,

    @Column(name = "income_total", nullable = false)
    var incomeTotal: Long = 0,

    @Column(name = "expense_total", nullable = false)
    var expenseTotal: Long = 0,

    @Column(nullable = false)
    var balance: Long = 0,

    @Column(name = "source_filename", nullable = false, length = 255)
    val sourceFilename: String,

    @Column(name = "uploaded_by", nullable = false)
    val uploadedBy: Long,

    @Column(name = "checksum_mismatch", nullable = false)
    var checksumMismatch: Boolean = false,

    /** 양식 합계셀(C70) 캐시값 — 불일치 시 차이 금액 표시용 */
    @Column(name = "form_income_total")
    var formIncomeTotal: Long? = null,

    /** 양식 합계셀(G70) 캐시값 — 불일치 시 차이 금액 표시용 */
    @Column(name = "form_expense_total")
    var formExpenseTotal: Long? = null,
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
}
