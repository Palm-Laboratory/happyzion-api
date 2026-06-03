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
    val hasData: Boolean,
    val incomeTotal: Long,
    val expenseTotal: Long,
    val balance: Long,
    val incomeByMajor: List<MajorBreakdown>,
    val expenseByMajor: List<MajorBreakdown>,
    val previousSummary: StatSummary?,
    val yoySummary: StatSummary?,
)

data class MajorBreakdown(val major: String, val amount: Long)

data class StatSummary(
    val incomeTotal: Long,
    val expenseTotal: Long,
    val balance: Long,
    val incomeByMajor: List<MajorBreakdown> = emptyList(),
    val expenseByMajor: List<MajorBreakdown> = emptyList(),
)

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
    val cumulativeStartBalance: Long = 0,
    val yoySummary: StatSummary? = null,
    val yoyCumulativeStartBalance: Long = 0,
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
                val prevReportIds = prevReports.map { it.id }
                val prevLines = if (prevReportIds.isEmpty()) emptyList() else lineRepo.findAllByReportIdIn(prevReportIds)
                val prevLinesByReport = prevLines.groupBy { it.reportId }
                // 직전 월의 마지막 주 (가장 큰 week 번호)
                val prevLastWeekReport = prevReports.maxByOrNull { it.week }

                // 전년 동기 데이터 (같은 년도-1, 같은 월)
                val yoyReports = reportRepo.findAllByYearAndMonth(y - 1, m)
                val yoyReportIds = yoyReports.map { it.id }
                val yoyLines = if (yoyReportIds.isEmpty()) emptyList() else lineRepo.findAllByReportIdIn(yoyReportIds)
                val yoyLinesByReport = yoyLines.groupBy { it.reportId }

                // 달력상 주일 수가 기본 상한이나, 그보다 큰 week가 저장돼 있으면(예: 4주 달의 5주)
                // 그 보고서가 주별 분해에서 누락되어 월 합계와 어긋나므로 실제 최대 week까지 버킷을 만든다.
                val maxWeek = maxOf(
                    FinancePeriodUtil.sundayCountInMonth(y, m),
                    reports.maxOfOrNull { it.week } ?: 0,
                )
                val buckets = (1..maxWeek).map { w ->
                    val r = reports.find { it.week == w }
                    val prevR = if (w == 1) prevLastWeekReport else reports.find { it.week == w - 1 }
                    val prevRLines = if (w == 1)
                        (prevLastWeekReport?.let { prevLinesByReport[it.id] } ?: emptyList())
                    else
                        (prevR?.let { linesByReport[it.id] } ?: emptyList())
                    val prevSummary = prevR?.let {
                        StatSummary(it.incomeTotal, it.expenseTotal, it.balance,
                            majorBreakdown(prevRLines, categories, FinanceDirection.INCOME),
                            majorBreakdown(prevRLines, categories, FinanceDirection.EXPENSE))
                    }
                    val yoyR = yoyReports.find { it.week == w }
                    val yoyRLines = yoyR?.let { yoyLinesByReport[it.id] } ?: emptyList()
                    val yoySummary = yoyR?.let {
                        StatSummary(it.incomeTotal, it.expenseTotal, it.balance,
                            majorBreakdown(yoyRLines, categories, FinanceDirection.INCOME),
                            majorBreakdown(yoyRLines, categories, FinanceDirection.EXPENSE))
                    }
                    if (r == null) {
                        StatBucket("${w}주", false, 0, 0, 0, emptyList(), emptyList(), prevSummary, yoySummary)
                    } else {
                        val rLines = linesByReport[r.id] ?: emptyList()
                        buildBucket("${w}주", r.incomeTotal, r.expenseTotal, r.balance, rLines, categories, prevSummary, yoySummary = yoySummary)
                    }
                }

                val summary = StatSummary(reports.sumOf { it.incomeTotal }, reports.sumOf { it.expenseTotal }, reports.sumOf { it.balance })
                val previousSummary = if (prevReports.isEmpty()) null else StatSummary(
                    prevReports.sumOf { it.incomeTotal }, prevReports.sumOf { it.expenseTotal }, prevReports.sumOf { it.balance },
                    majorBreakdown(prevLines, categories, FinanceDirection.INCOME),
                    majorBreakdown(prevLines, categories, FinanceDirection.EXPENSE))
                val yoySummary = if (yoyReports.isEmpty()) null else StatSummary(
                    yoyReports.sumOf { it.incomeTotal }, yoyReports.sumOf { it.expenseTotal }, yoyReports.sumOf { it.balance },
                    majorBreakdown(yoyLines, categories, FinanceDirection.INCOME),
                    majorBreakdown(yoyLines, categories, FinanceDirection.EXPENSE))

                val allLines = if (reportIds.isEmpty()) emptyList() else lineRepo.findAllByReportIdIn(reportIds)
                val allReports = reportRepo.findAll()
                val weekCumulativeStart = allReports
                    .filter { it.year < y || (it.year == y && it.month < m) }
                    .sumOf { it.balance }
                val yoyCumulativeStart = allReports
                    .filter { it.year < y - 1 || (it.year == y - 1 && it.month < m) }
                    .sumOf { it.balance }
                FinanceStatResult(granularity, y, m, buckets, summary, previousSummary,
                    if (prevReports.isEmpty()) null else "${prevY}년 ${prevM}월",
                    majorBreakdown(allLines, categories, FinanceDirection.INCOME),
                    majorBreakdown(allLines, categories, FinanceDirection.EXPENSE),
                    cumulativeStartBalance = weekCumulativeStart,
                    yoySummary = yoySummary,
                    yoyCumulativeStartBalance = yoyCumulativeStart,
                )
            }

            StatGranularity.MONTH -> {
                val y = year ?: return emptyResult(granularity, year, month)
                val reports = reportRepo.findAllByYear(y)
                val reportIds = reports.map { it.id }
                val lines = if (reportIds.isEmpty()) emptyList() else lineRepo.findAllByReportIdIn(reportIds)
                val prevReports = reportRepo.findAllByYear(y - 1)
                val prevReportIds = prevReports.map { it.id }
                val prevLines = if (prevReportIds.isEmpty()) emptyList() else lineRepo.findAllByReportIdIn(prevReportIds)

                val buckets = (1..12).map { m ->
                    val mReports = reports.filter { it.month == m }
                    val mLines = lines.filter { l -> mReports.any { it.id == l.reportId } }
                    val prevMReports = if (m == 1) prevReports.filter { it.month == 12 } else reports.filter { it.month == m - 1 }
                    val prevMLines = if (m == 1)
                        prevLines.filter { l -> prevMReports.any { it.id == l.reportId } }
                    else
                        lines.filter { l -> prevMReports.any { it.id == l.reportId } }
                    val prevSummary = if (prevMReports.isEmpty()) null else StatSummary(
                        prevMReports.sumOf { it.incomeTotal }, prevMReports.sumOf { it.expenseTotal }, prevMReports.sumOf { it.balance },
                        majorBreakdown(prevMLines, categories, FinanceDirection.INCOME),
                        majorBreakdown(prevMLines, categories, FinanceDirection.EXPENSE))
                    // 전년 동기: 전년도 같은 월
                    val yoyMReports = prevReports.filter { it.month == m }
                    val yoyMLines = prevLines.filter { l -> yoyMReports.any { it.id == l.reportId } }
                    val yoySummary = if (yoyMReports.isEmpty()) null else StatSummary(
                        yoyMReports.sumOf { it.incomeTotal }, yoyMReports.sumOf { it.expenseTotal }, yoyMReports.sumOf { it.balance },
                        majorBreakdown(yoyMLines, categories, FinanceDirection.INCOME),
                        majorBreakdown(yoyMLines, categories, FinanceDirection.EXPENSE))
                    buildBucket("${m}월",
                        mReports.sumOf { it.incomeTotal }, mReports.sumOf { it.expenseTotal }, mReports.sumOf { it.balance },
                        mLines, categories, prevSummary, hasData = mReports.isNotEmpty(), yoySummary = yoySummary)
                }

                val summary = StatSummary(reports.sumOf { it.incomeTotal }, reports.sumOf { it.expenseTotal }, reports.sumOf { it.balance })
                val previousSummary = if (prevReports.isEmpty()) null else StatSummary(
                    prevReports.sumOf { it.incomeTotal }, prevReports.sumOf { it.expenseTotal }, prevReports.sumOf { it.balance },
                    majorBreakdown(prevLines, categories, FinanceDirection.INCOME),
                    majorBreakdown(prevLines, categories, FinanceDirection.EXPENSE))
                val allReportsForMonth = reportRepo.findAll()
                val monthCumulativeStart = allReportsForMonth.filter { it.year < y }.sumOf { it.balance }
                val yoyCumulativeStart = allReportsForMonth.filter { it.year < y - 1 }.sumOf { it.balance }

                FinanceStatResult(granularity, y, null, buckets, summary, previousSummary,
                    if (prevReports.isEmpty()) null else "${y - 1}년",
                    majorBreakdown(lines, categories, FinanceDirection.INCOME),
                    majorBreakdown(lines, categories, FinanceDirection.EXPENSE),
                    yoySummary = previousSummary,
                    cumulativeStartBalance = monthCumulativeStart,
                    yoyCumulativeStartBalance = yoyCumulativeStart,
                )
            }

            StatGranularity.QUARTER -> {
                val y = year ?: return emptyResult(granularity, year, month)
                val reports = reportRepo.findAllByYear(y)
                val reportIds = reports.map { it.id }
                val lines = if (reportIds.isEmpty()) emptyList() else lineRepo.findAllByReportIdIn(reportIds)
                val prevReports = reportRepo.findAllByYear(y - 1)
                val prevReportIds = prevReports.map { it.id }
                val prevLines = if (prevReportIds.isEmpty()) emptyList() else lineRepo.findAllByReportIdIn(prevReportIds)

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
                    val prevQLines = if (q == 1)
                        prevLines.filter { l -> prevQReports.any { it.id == l.reportId } }
                    else
                        lines.filter { l -> prevQReports.any { it.id == l.reportId } }
                    val prevSummary = if (prevQReports.isEmpty()) null else StatSummary(
                        prevQReports.sumOf { it.incomeTotal }, prevQReports.sumOf { it.expenseTotal }, prevQReports.sumOf { it.balance },
                        majorBreakdown(prevQLines, categories, FinanceDirection.INCOME),
                        majorBreakdown(prevQLines, categories, FinanceDirection.EXPENSE))
                    // 전년 동기: 전년도 같은 분기
                    val yoyQReports = prevReports.filter { it.month in months }
                    val yoyQLines = prevLines.filter { l -> yoyQReports.any { it.id == l.reportId } }
                    val yoySummary = if (yoyQReports.isEmpty()) null else StatSummary(
                        yoyQReports.sumOf { it.incomeTotal }, yoyQReports.sumOf { it.expenseTotal }, yoyQReports.sumOf { it.balance },
                        majorBreakdown(yoyQLines, categories, FinanceDirection.INCOME),
                        majorBreakdown(yoyQLines, categories, FinanceDirection.EXPENSE))
                    buildBucket("${q}분기",
                        qReports.sumOf { it.incomeTotal }, qReports.sumOf { it.expenseTotal }, qReports.sumOf { it.balance },
                        qLines, categories, prevSummary, hasData = qReports.isNotEmpty(), yoySummary = yoySummary)
                }

                val summary = StatSummary(reports.sumOf { it.incomeTotal }, reports.sumOf { it.expenseTotal }, reports.sumOf { it.balance })
                val previousSummary = if (prevReports.isEmpty()) null else StatSummary(
                    prevReports.sumOf { it.incomeTotal }, prevReports.sumOf { it.expenseTotal }, prevReports.sumOf { it.balance },
                    majorBreakdown(prevLines, categories, FinanceDirection.INCOME),
                    majorBreakdown(prevLines, categories, FinanceDirection.EXPENSE))
                val allReportsForQuarter = reportRepo.findAll()
                val quarterCumulativeStart = allReportsForQuarter.filter { it.year < y }.sumOf { it.balance }
                val yoyCumulativeStart = allReportsForQuarter.filter { it.year < y - 1 }.sumOf { it.balance }

                FinanceStatResult(granularity, y, null, buckets, summary, previousSummary,
                    if (prevReports.isEmpty()) null else "${y - 1}년",
                    majorBreakdown(lines, categories, FinanceDirection.INCOME),
                    majorBreakdown(lines, categories, FinanceDirection.EXPENSE),
                    cumulativeStartBalance = quarterCumulativeStart,
                    yoySummary = previousSummary,
                    yoyCumulativeStartBalance = yoyCumulativeStart,
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
                    val prevYLines = allLines.filter { l -> prevYReports.any { it.id == l.reportId } }
                    val prevSummary = if (prevYReports.isEmpty()) null else StatSummary(
                        prevYReports.sumOf { it.incomeTotal }, prevYReports.sumOf { it.expenseTotal }, prevYReports.sumOf { it.balance },
                        majorBreakdown(prevYLines, categories, FinanceDirection.INCOME),
                        majorBreakdown(prevYLines, categories, FinanceDirection.EXPENSE))
                    buildBucket("${y}년",
                        yReports.sumOf { it.incomeTotal }, yReports.sumOf { it.expenseTotal }, yReports.sumOf { it.balance },
                        yLines, categories, prevSummary, yoySummary = prevSummary)
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
        hasData: Boolean = true,
        yoySummary: StatSummary? = null,
    ) = StatBucket(
        label = label,
        hasData = hasData,
        incomeTotal = incomeTotal, expenseTotal = expenseTotal, balance = balance,
        incomeByMajor = majorBreakdown(lines, categories, FinanceDirection.INCOME),
        expenseByMajor = majorBreakdown(lines, categories, FinanceDirection.EXPENSE),
        previousSummary = previousSummary,
        yoySummary = yoySummary,
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
