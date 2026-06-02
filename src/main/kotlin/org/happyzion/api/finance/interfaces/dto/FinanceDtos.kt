package org.happyzion.api.finance.interfaces.dto

import org.happyzion.api.finance.application.*
import org.happyzion.api.finance.domain.FinanceDirection
import java.time.LocalDate
import java.time.OffsetDateTime

// ── 파싱 미리보기 응답 ────────────────────────────────────────────────────────

data class FinancePeriodDto(val year: Int, val month: Int, val week: Int)

data class FinanceParsedLineDto(
    val direction: String,
    val major: String,
    val minor: String,
    val amount: Long,
    val detail: String? = null,
)

data class FinanceParsedUnexecutedItemDto(
    val content: String,
    val amount: Long,
    val executedDate: String?,
    val note: String?,
)

data class FinanceParsedTotalsDto(
    val incomeTotal: Long,
    val expenseTotal: Long,
    val balance: Long,
    val formCellIncomeTotal: Long?,
    val formCellExpenseTotal: Long?,
    val checksumMismatch: Boolean,
)

data class FinancePreviewResponse(
    val sourceFilename: String,
    val period: FinancePeriodDto?,
    val periodSourceText: String?,
    val isDuplicate: Boolean,
    val lines: List<FinanceParsedLineDto>,
    val unexecutedItems: List<FinanceParsedUnexecutedItemDto>,
    val totals: FinanceParsedTotalsDto,
)

// ── 저장 요청 ─────────────────────────────────────────────────────────────────

data class FinanceReportSaveRequest(
    val year: Int,
    val month: Int,
    val week: Int,
)

// ── 보고서 목록 응답 ──────────────────────────────────────────────────────────

data class FinanceReportSummaryResponse(
    val id: Long,
    val year: Int,
    val month: Int,
    val week: Int,
    val incomeTotal: Long,
    val expenseTotal: Long,
    val balance: Long,
    val sourceFilename: String,
    val uploadedAt: OffsetDateTime,
    val checksumMismatch: Boolean,
)

data class FinanceReportPageResponse(
    val items: List<FinanceReportSummaryResponse>,
    val total: Long,
    val hasNext: Boolean,
)

// ── 보고서 상세 응답 ──────────────────────────────────────────────────────────

data class FinanceLineResponse(
    val categoryId: Long,
    val direction: String,
    val major: String,
    val minor: String,
    val amount: Long,
    val detail: String? = null,
)

data class FinanceUnexecutedItemResponse(
    val id: Long,
    val content: String,
    val amount: Long,
    val executedDate: LocalDate?,
    val note: String?,
    val sortOrder: Int,
)

data class FinanceReportDetailResponse(
    val id: Long,
    val year: Int,
    val month: Int,
    val week: Int,
    val incomeTotal: Long,
    val expenseTotal: Long,
    val balance: Long,
    val sourceFilename: String,
    val uploadedAt: OffsetDateTime,
    val checksumMismatch: Boolean,
    /** 양식 합계셀 값 — 불일치 시 차이 금액 표시용 */
    val formIncomeTotal: Long?,
    val formExpenseTotal: Long?,
    val lines: List<FinanceLineResponse>,
    val unexecutedItems: List<FinanceUnexecutedItemResponse>,
)

// ── 통계 응답 ─────────────────────────────────────────────────────────────────

data class MajorBreakdownResponse(val major: String, val amount: Long)

data class StatBucketResponse(
    val label: String,
    val hasData: Boolean,
    val incomeTotal: Long,
    val expenseTotal: Long,
    val balance: Long,
    val incomeByMajor: List<MajorBreakdownResponse>,
    val expenseByMajor: List<MajorBreakdownResponse>,
    val previousSummary: StatSummaryResponse?,
    val yoySummary: StatSummaryResponse?,
)

data class StatSummaryResponse(
    val incomeTotal: Long,
    val expenseTotal: Long,
    val balance: Long,
    val incomeByMajor: List<MajorBreakdownResponse> = emptyList(),
    val expenseByMajor: List<MajorBreakdownResponse> = emptyList(),
)

data class FinanceStatResponse(
    val granularity: String,
    val year: Int?,
    val month: Int?,
    val buckets: List<StatBucketResponse>,
    val summary: StatSummaryResponse,
    val previousSummary: StatSummaryResponse?,
    val previousLabel: String?,
    val incomeByMajor: List<MajorBreakdownResponse>,
    val expenseByMajor: List<MajorBreakdownResponse>,
    val cumulativeStartBalance: Long,
    val yoySummary: StatSummaryResponse?,
    val yoyCumulativeStartBalance: Long,
)

// ── 변환 함수 ─────────────────────────────────────────────────────────────────

fun FinanceParseResult.toPreviewResponse(isDuplicate: Boolean) = FinancePreviewResponse(
    sourceFilename = sourceFilename,
    period = period?.let { FinancePeriodDto(it.year, it.month, it.week) },
    periodSourceText = periodSourceText,
    isDuplicate = isDuplicate,
    lines = (incomeLines.map { FinanceParsedLineDto("INCOME", it.major, it.minor, it.amount) } +
            expenseLines.map { FinanceParsedLineDto("EXPENSE", it.major, it.minor, it.amount, it.detail) }),
    unexecutedItems = unexecutedItems.map { FinanceParsedUnexecutedItemDto(it.content, it.amount, it.executedDate, it.note) },
    totals = FinanceParsedTotalsDto(incomeTotal, expenseTotal, balance, formIncomeTotal, formExpenseTotal, checksumMismatch),
)

fun FinanceReportSummary.toResponse() = FinanceReportSummaryResponse(
    id = id, year = year, month = month, week = week,
    incomeTotal = incomeTotal, expenseTotal = expenseTotal, balance = balance,
    sourceFilename = sourceFilename,
    uploadedAt = createdAt,
    checksumMismatch = checksumMismatch,
)

fun FinanceReportDetail.toDetailResponse() = FinanceReportDetailResponse(
    id = id, year = year, month = month, week = week,
    incomeTotal = incomeTotal, expenseTotal = expenseTotal, balance = balance,
    sourceFilename = sourceFilename, uploadedAt = createdAt, checksumMismatch = checksumMismatch,
    formIncomeTotal = formIncomeTotal,
    formExpenseTotal = formExpenseTotal,
    lines = lines.map { FinanceLineResponse(it.categoryId, it.direction.name, it.major, it.minor, it.amount, it.detail) },
    unexecutedItems = unexecutedItems.map { FinanceUnexecutedItemResponse(it.id, it.content, it.amount, it.executedDate, it.note, it.sortOrder) },
)

fun FinanceStatResult.toResponse() = FinanceStatResponse(
    granularity = granularity.name,
    year = year, month = month,
    buckets = buckets.map {
        StatBucketResponse(it.label, it.hasData, it.incomeTotal, it.expenseTotal, it.balance,
            it.incomeByMajor.map { b -> MajorBreakdownResponse(b.major, b.amount) },
            it.expenseByMajor.map { b -> MajorBreakdownResponse(b.major, b.amount) },
            it.previousSummary?.let { p -> StatSummaryResponse(p.incomeTotal, p.expenseTotal, p.balance,
                p.incomeByMajor.map { b -> MajorBreakdownResponse(b.major, b.amount) },
                p.expenseByMajor.map { b -> MajorBreakdownResponse(b.major, b.amount) }) },
            it.yoySummary?.let { p -> StatSummaryResponse(p.incomeTotal, p.expenseTotal, p.balance,
                p.incomeByMajor.map { b -> MajorBreakdownResponse(b.major, b.amount) },
                p.expenseByMajor.map { b -> MajorBreakdownResponse(b.major, b.amount) }) })
    },
    summary = StatSummaryResponse(summary.incomeTotal, summary.expenseTotal, summary.balance),
    previousSummary = previousSummary?.let { StatSummaryResponse(it.incomeTotal, it.expenseTotal, it.balance,
        it.incomeByMajor.map { b -> MajorBreakdownResponse(b.major, b.amount) },
        it.expenseByMajor.map { b -> MajorBreakdownResponse(b.major, b.amount) }) },
    yoySummary = yoySummary?.let { StatSummaryResponse(it.incomeTotal, it.expenseTotal, it.balance,
        it.incomeByMajor.map { b -> MajorBreakdownResponse(b.major, b.amount) },
        it.expenseByMajor.map { b -> MajorBreakdownResponse(b.major, b.amount) }) },
    yoyCumulativeStartBalance = yoyCumulativeStartBalance,
    previousLabel = previousLabel,
    incomeByMajor = incomeByMajor.map { MajorBreakdownResponse(it.major, it.amount) },
    expenseByMajor = expenseByMajor.map { MajorBreakdownResponse(it.major, it.amount) },
    cumulativeStartBalance = cumulativeStartBalance,
)
