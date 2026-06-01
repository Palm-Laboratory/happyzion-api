package org.happyzion.api.finance.application

import org.happyzion.api.finance.domain.FinanceDirection
import org.happyzion.api.finance.infrastructure.persistence.FinanceCategoryRepository
import org.happyzion.api.finance.infrastructure.persistence.FinanceReportLineRepository
import org.happyzion.api.finance.infrastructure.persistence.FinanceReportRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate

enum class StatGranularity { WEEK, MONTH, QUARTER, YEAR }

data class StatBucket(
    val label: String,
    val incomeTotal: Long,
    val expenseTotal: Long,
    val balance: Long,
    val incomeByMajor: List<MajorBreakdown>,
    val expenseByMajor: List<MajorBreakdown>,
    val previousSummary: StatSummary?,
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

                // 직전 월 데이터 (1주차의 전기 비교용)
                val prevM = if (m == 1) 12 else m - 1
                val prevY = if (m == 1) y - 1 else y
                val prevReports = reportRepo.findAllByYearAndMonth(prevY, prevM)
                // 직전 월의 마지막 주 (가장 큰 week 번호)
                val prevLastWeekReport = prevReports.maxByOrNull { it.week }

                val maxWeek = sundayCountInMonth(y, m)
                val buckets = (1..maxWeek).map { w ->
                    val r = reports.find { it.week == w }
                    val prevR = if (w == 1) prevLastWeekReport else reports.find { it.week == w - 1 }
                    val prevSummary = prevR?.let { StatSummary(it.incomeTotal, it.expenseTotal, it.balance) }
                    if (r == null) {
                        StatBucket("${w}주", 0, 0, 0, emptyList(), emptyList(), prevSummary)
                    } else {
                        val rLines = linesByReport[r.id] ?: emptyList()
                        buildBucket("${w}주", r.incomeTotal, r.expenseTotal, r.balance, rLines, categories, prevSummary)
                    }
                }

                val summary = StatSummary(reports.sumOf { it.incomeTotal }, reports.sumOf { it.expenseTotal }, reports.sumOf { it.balance })
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
                val prevReports = reportRepo.findAllByYear(y - 1)

                val buckets = (1..12).map { m ->
                    val mReports = reports.filter { it.month == m }
                    val mLines = lines.filter { l -> mReports.any { it.id == l.reportId } }
                    val prevMReports = if (m == 1) prevReports.filter { it.month == 12 } else reports.filter { it.month == m - 1 }
                    val prevSummary = if (prevMReports.isEmpty()) null else StatSummary(prevMReports.sumOf { it.incomeTotal }, prevMReports.sumOf { it.expenseTotal }, prevMReports.sumOf { it.balance })
                    buildBucket("${m}월",
                        mReports.sumOf { it.incomeTotal }, mReports.sumOf { it.expenseTotal }, mReports.sumOf { it.balance },
                        mLines, categories, prevSummary)
                }

                val summary = StatSummary(reports.sumOf { it.incomeTotal }, reports.sumOf { it.expenseTotal }, reports.sumOf { it.balance })
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
                val prevReports = reportRepo.findAllByYear(y - 1)

                val buckets = (1..4).map { q ->
                    val months = listOf(q * 3 - 2, q * 3 - 1, q * 3)
                    val qReports = reports.filter { it.month in months }
                    val qLines = lines.filter { l -> qReports.any { it.id == l.reportId } }
                    val prevQReports = if (q == 1) {
                        val prevMonths = listOf(10, 11, 12)
                        prevReports.filter { it.month in prevMonths }
                    } else {
                        val prevMonths = listOf((q - 1) * 3 - 2, (q - 1) * 3 - 1, (q - 1) * 3)
                        reports.filter { it.month in prevMonths }
                    }
                    val prevSummary = if (prevQReports.isEmpty()) null else StatSummary(prevQReports.sumOf { it.incomeTotal }, prevQReports.sumOf { it.expenseTotal }, prevQReports.sumOf { it.balance })
                    buildBucket("${q}분기",
                        qReports.sumOf { it.incomeTotal }, qReports.sumOf { it.expenseTotal }, qReports.sumOf { it.balance },
                        qLines, categories, prevSummary)
                }

                val summary = StatSummary(reports.sumOf { it.incomeTotal }, reports.sumOf { it.expenseTotal }, reports.sumOf { it.balance })
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

                val buckets = years.mapIndexed { idx, y ->
                    val yReports = allReports.filter { it.year == y }
                    val yLines = allLines.filter { l -> yReports.any { it.id == l.reportId } }
                    val prevYear = if (idx > 0) years[idx - 1] else null
                    val prevYReports = if (prevYear != null) allReports.filter { it.year == prevYear } else emptyList()
                    val prevSummary = if (prevYReports.isEmpty()) null else StatSummary(prevYReports.sumOf { it.incomeTotal }, prevYReports.sumOf { it.expenseTotal }, prevYReports.sumOf { it.balance })
                    buildBucket("${y}년",
                        yReports.sumOf { it.incomeTotal }, yReports.sumOf { it.expenseTotal }, yReports.sumOf { it.balance },
                        yLines, categories, prevSummary)
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
        previousSummary: StatSummary? = null,
    ) = StatBucket(
        label = label,
        incomeTotal = incomeTotal, expenseTotal = expenseTotal, balance = balance,
        incomeByMajor = majorBreakdown(lines, categories, FinanceDirection.INCOME),
        expenseByMajor = majorBreakdown(lines, categories, FinanceDirection.EXPENSE),
        previousSummary = previousSummary,
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

    private fun sundayCountInMonth(year: Int, month: Int): Int {
        val firstDay = LocalDate.of(year, month, 1)
        val lastDay = firstDay.lengthOfMonth()
        val firstSunday = (1..7).first { LocalDate.of(year, month, it).dayOfWeek == DayOfWeek.SUNDAY }
        return generateSequence(firstSunday) { it + 7 }.takeWhile { it <= lastDay }.count()
    }

    private fun emptyResult(granularity: StatGranularity, year: Int?, month: Int?) =
        FinanceStatResult(granularity, year, month, emptyList(), StatSummary(0, 0, 0), null, null, emptyList(), emptyList())
}
