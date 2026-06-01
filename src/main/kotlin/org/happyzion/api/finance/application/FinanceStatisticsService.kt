package org.happyzion.api.finance.application

import org.happyzion.api.finance.domain.FinanceDirection
import org.happyzion.api.finance.infrastructure.persistence.FinanceCategoryRepository
import org.happyzion.api.finance.infrastructure.persistence.FinanceReportLineRepository
import org.happyzion.api.finance.infrastructure.persistence.FinanceReportRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

enum class StatGranularity { WEEK, MONTH, QUARTER, YEAR }

data class StatBucket(
    val label: String,
    val incomeTotal: Long,
    val expenseTotal: Long,
    val balance: Long,
    val incomeByMajor: List<MajorBreakdown>,
    val expenseByMajor: List<MajorBreakdown>,
)

data class MajorBreakdown(val major: String, val amount: Long)

data class StatSummary(val incomeTotal: Long, val expenseTotal: Long, val balance: Long)

data class FinanceStatResult(
    val granularity: StatGranularity,
    val year: Int?,
    val month: Int?,
    val buckets: List<StatBucket>,
    val summary: StatSummary,
    val previousSummary: StatSummary?,
    val previousLabel: String?,
    val incomeByMajor: List<MajorBreakdown>,
    val expenseByMajor: List<MajorBreakdown>,
)

@Service
@Transactional(readOnly = true)
class FinanceStatisticsService(
    private val reportRepo: FinanceReportRepository,
    private val lineRepo: FinanceReportLineRepository,
    private val categoryRepo: FinanceCategoryRepository,
) {
    fun statistics(granularity: StatGranularity, year: Int?, month: Int?): FinanceStatResult {
        val categories = categoryRepo.findAll().associateBy { it.id }

        return when (granularity) {
            StatGranularity.WEEK -> {
                val y = year ?: return emptyResult(granularity, year, month)
                val m = month ?: return emptyResult(granularity, year, month)
                val reports = reportRepo.findAllByYearAndMonth(y, m)
                val reportIds = reports.map { it.id }
                val lines = if (reportIds.isEmpty()) emptyList() else lineRepo.findAllByReportIdIn(reportIds)
                val linesByReport = lines.groupBy { it.reportId }

                val buckets = (1..5).map { w ->
                    val r = reports.find { it.week == w }
                    if (r == null) {
                        StatBucket("${w}주", 0, 0, 0, emptyList(), emptyList())
                    } else {
                        val rLines = linesByReport[r.id] ?: emptyList()
                        buildBucket("${w}주", r.incomeTotal, r.expenseTotal, r.balance, rLines, categories)
                    }
                }

                val summary = StatSummary(reports.sumOf { it.incomeTotal }, reports.sumOf { it.expenseTotal }, reports.sumOf { it.balance })
                val prevM = if (m == 1) 12 else m - 1
                val prevY = if (m == 1) y - 1 else y
                val prevReports = reportRepo.findAllByYearAndMonth(prevY, prevM)
                val previousSummary = if (prevReports.isEmpty()) null else StatSummary(prevReports.sumOf { it.incomeTotal }, prevReports.sumOf { it.expenseTotal }, prevReports.sumOf { it.balance })

                val allLines = if (reportIds.isEmpty()) emptyList() else lineRepo.findAllByReportIdIn(reportIds)
                FinanceStatResult(granularity, y, m, buckets, summary, previousSummary,
                    if (prevReports.isEmpty()) null else "${prevY}년 ${prevM}월",
                    majorBreakdown(allLines, categories, FinanceDirection.INCOME),
                    majorBreakdown(allLines, categories, FinanceDirection.EXPENSE),
                )
            }

            StatGranularity.MONTH -> {
                val y = year ?: return emptyResult(granularity, year, month)
                val reports = reportRepo.findAllByYear(y)
                val reportIds = reports.map { it.id }
                val lines = if (reportIds.isEmpty()) emptyList() else lineRepo.findAllByReportIdIn(reportIds)

                val buckets = (1..12).map { m ->
                    val mReports = reports.filter { it.month == m }
                    val mLines = lines.filter { l -> mReports.any { it.id == l.reportId } }
                    buildBucket("${m}월",
                        mReports.sumOf { it.incomeTotal }, mReports.sumOf { it.expenseTotal }, mReports.sumOf { it.balance },
                        mLines, categories)
                }

                val summary = StatSummary(reports.sumOf { it.incomeTotal }, reports.sumOf { it.expenseTotal }, reports.sumOf { it.balance })
                val prevReports = reportRepo.findAllByYear(y - 1)
                val previousSummary = if (prevReports.isEmpty()) null else StatSummary(prevReports.sumOf { it.incomeTotal }, prevReports.sumOf { it.expenseTotal }, prevReports.sumOf { it.balance })

                FinanceStatResult(granularity, y, null, buckets, summary, previousSummary,
                    if (prevReports.isEmpty()) null else "${y - 1}년",
                    majorBreakdown(lines, categories, FinanceDirection.INCOME),
                    majorBreakdown(lines, categories, FinanceDirection.EXPENSE),
                )
            }

            StatGranularity.QUARTER -> {
                val y = year ?: return emptyResult(granularity, year, month)
                val reports = reportRepo.findAllByYear(y)
                val reportIds = reports.map { it.id }
                val lines = if (reportIds.isEmpty()) emptyList() else lineRepo.findAllByReportIdIn(reportIds)

                val buckets = (1..4).map { q ->
                    val months = listOf(q * 3 - 2, q * 3 - 1, q * 3)
                    val qReports = reports.filter { it.month in months }
                    val qLines = lines.filter { l -> qReports.any { it.id == l.reportId } }
                    buildBucket("${q}분기",
                        qReports.sumOf { it.incomeTotal }, qReports.sumOf { it.expenseTotal }, qReports.sumOf { it.balance },
                        qLines, categories)
                }

                val summary = StatSummary(reports.sumOf { it.incomeTotal }, reports.sumOf { it.expenseTotal }, reports.sumOf { it.balance })
                val prevReports = reportRepo.findAllByYear(y - 1)
                val previousSummary = if (prevReports.isEmpty()) null else StatSummary(prevReports.sumOf { it.incomeTotal }, prevReports.sumOf { it.expenseTotal }, prevReports.sumOf { it.balance })

                FinanceStatResult(granularity, y, null, buckets, summary, previousSummary,
                    if (prevReports.isEmpty()) null else "${y - 1}년",
                    majorBreakdown(lines, categories, FinanceDirection.INCOME),
                    majorBreakdown(lines, categories, FinanceDirection.EXPENSE),
                )
            }

            StatGranularity.YEAR -> {
                val years = reportRepo.findDistinctYears()
                val allReports = reportRepo.findAll()
                val allIds = allReports.map { it.id }
                val allLines = if (allIds.isEmpty()) emptyList() else lineRepo.findAllByReportIdIn(allIds)

                val buckets = years.map { y ->
                    val yReports = allReports.filter { it.year == y }
                    val yLines = allLines.filter { l -> yReports.any { it.id == l.reportId } }
                    buildBucket("${y}년",
                        yReports.sumOf { it.incomeTotal }, yReports.sumOf { it.expenseTotal }, yReports.sumOf { it.balance },
                        yLines, categories)
                }

                val summary = StatSummary(allReports.sumOf { it.incomeTotal }, allReports.sumOf { it.expenseTotal }, allReports.sumOf { it.balance })
                FinanceStatResult(granularity, null, null, buckets, summary, null, null,
                    majorBreakdown(allLines, categories, FinanceDirection.INCOME),
                    majorBreakdown(allLines, categories, FinanceDirection.EXPENSE),
                )
            }
        }
    }

    private fun buildBucket(
        label: String,
        incomeTotal: Long, expenseTotal: Long, balance: Long,
        lines: List<org.happyzion.api.finance.domain.FinanceReportLine>,
        categories: Map<Long, org.happyzion.api.finance.domain.FinanceCategory>,
    ) = StatBucket(
        label = label,
        incomeTotal = incomeTotal, expenseTotal = expenseTotal, balance = balance,
        incomeByMajor = majorBreakdown(lines, categories, FinanceDirection.INCOME),
        expenseByMajor = majorBreakdown(lines, categories, FinanceDirection.EXPENSE),
    )

    private fun majorBreakdown(
        lines: List<org.happyzion.api.finance.domain.FinanceReportLine>,
        categories: Map<Long, org.happyzion.api.finance.domain.FinanceCategory>,
        direction: FinanceDirection,
    ): List<MajorBreakdown> = lines
        .mapNotNull { l -> categories[l.categoryId]?.takeIf { it.direction == direction }?.let { it.major to l.amount } }
        .groupBy({ it.first }, { it.second })
        .map { (major, amounts) -> MajorBreakdown(major, amounts.sum()) }
        .filter { it.amount > 0 }
        .sortedByDescending { it.amount }

    private fun emptyResult(granularity: StatGranularity, year: Int?, month: Int?) =
        FinanceStatResult(granularity, year, month, emptyList(), StatSummary(0, 0, 0), null, null, emptyList(), emptyList())
}
