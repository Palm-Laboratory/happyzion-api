package org.happyzion.api.finance.application

import org.happyzion.api.finance.domain.*
import org.happyzion.api.finance.infrastructure.persistence.*
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

data class FinanceReportSummary(
    val id: Long,
    val year: Int,
    val month: Int,
    val week: Int,
    val incomeTotal: Long,
    val expenseTotal: Long,
    val balance: Long,
    val sourceFilename: String,
    val uploadedBy: Long,
    val checksumMismatch: Boolean,
    val createdAt: java.time.OffsetDateTime,
)

data class FinanceReportDetail(
    val id: Long,
    val year: Int,
    val month: Int,
    val week: Int,
    val incomeTotal: Long,
    val expenseTotal: Long,
    val balance: Long,
    val sourceFilename: String,
    val uploadedBy: Long,
    val checksumMismatch: Boolean,
    val formIncomeTotal: Long?,
    val formExpenseTotal: Long?,
    val lines: List<FinanceLineSummary>,
    val unexecutedItems: List<FinanceUnexecutedSummary>,
    val createdAt: java.time.OffsetDateTime,
)

data class FinanceLineSummary(
    val categoryId: Long,
    val direction: FinanceDirection,
    val major: String,
    val minor: String,
    val amount: Long,
    val detail: String? = null,
)

data class FinanceUnexecutedSummary(
    val id: Long,
    val content: String,
    val amount: Long,
    val executedDate: java.time.LocalDate?,
    val note: String?,
    val sortOrder: Int,
)

data class FinanceReportPage(
    val items: List<FinanceReportSummary>,
    val total: Long,
    val hasNext: Boolean,
)

data class FinancePreviewResult(
    val parseResult: FinanceParseResult,
    val isDuplicate: Boolean,
)

@Service
@Transactional
class FinanceReportService(
    private val reportRepo: FinanceReportRepository,
    private val lineRepo: FinanceReportLineRepository,
    private val unexecutedRepo: FinanceUnexecutedItemRepository,
    private val categoryRepo: FinanceCategoryRepository,
    private val parser: FinanceExcelParser,
) {
    /** 엑셀 업로드 → 파싱 미리보기. DB 저장 없음. */
    @Transactional(readOnly = true)
    fun preview(file: MultipartFile): FinancePreviewResult {
        val result = parser.parse(file.inputStream, file.originalFilename ?: file.name)
        val isDuplicate = result.period?.let {
            reportRepo.findByYearAndMonthAndWeek(it.year, it.month, it.week) != null
        } ?: false
        return FinancePreviewResult(result, isDuplicate)
    }

    /** 파싱 결과를 확정 저장. 같은 기간이 이미 있으면 덮어씀. */
    fun save(
        parseResult: FinanceParseResult,
        year: Int, month: Int, week: Int,
        actorId: Long,
    ): FinanceReportDetail {
        // 카테고리 맵 (direction+major+minor → id)
        val categories = categoryRepo.findAllByActiveOrderBySortOrder()
        val catMap = categories.associateBy { Triple(it.direction.name, it.major, it.minor) }.toMutableMap()

        // 기존 보고서가 있으면 라인·미집행 삭제 후 재사용, 없으면 신규
        val existing = reportRepo.findByYearAndMonthAndWeek(year, month, week)
        val report = if (existing != null) {
            lineRepo.deleteAllByReportId(existing.id)
            unexecutedRepo.deleteAllByReportId(existing.id)
            existing.apply {
                this.incomeTotal = parseResult.incomeTotal
                this.expenseTotal = parseResult.expenseTotal
                this.balance = parseResult.balance
                this.checksumMismatch = parseResult.checksumMismatch
                this.formIncomeTotal = parseResult.formIncomeTotal
                this.formExpenseTotal = parseResult.formExpenseTotal
            }
        } else {
            reportRepo.save(
                FinanceReport(
                    year = year, month = month, week = week,
                    incomeTotal = parseResult.incomeTotal,
                    expenseTotal = parseResult.expenseTotal,
                    balance = parseResult.balance,
                    sourceFilename = parseResult.sourceFilename,
                    uploadedBy = actorId,
                    checksumMismatch = parseResult.checksumMismatch,
                    formIncomeTotal = parseResult.formIncomeTotal,
                    formExpenseTotal = parseResult.formExpenseTotal,
                )
            )
        }

        // 라인 저장
        val allLines = parseResult.incomeLines + parseResult.expenseLines
        val lines = allLines.mapNotNull { line ->
            val dir = if (line.isIncome) "INCOME" else "EXPENSE"
            val key = Triple(dir, line.major, line.minor)
            val cat = catMap[key] ?: run {
                val direction = if (line.isIncome) FinanceDirection.INCOME else FinanceDirection.EXPENSE
                val newCat = categoryRepo.save(FinanceCategory(direction = direction, major = line.major, minor = line.minor))
                catMap[key] = newCat
                newCat
            }
            lineRepo.save(FinanceReportLine(reportId = report.id, categoryId = cat.id, amount = line.amount, detail = line.detail))
            FinanceLineSummary(cat.id, cat.direction, cat.major, cat.minor, line.amount, line.detail)
        }

        // 미집행 품목 저장
        val unexecutedItems = parseResult.unexecutedItems.mapIndexed { idx, item ->
            val saved = unexecutedRepo.save(
                FinanceUnexecutedItem(
                    reportId = report.id,
                    content = item.content,
                    amount = item.amount,
                    executedDate = item.executedDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
                    note = item.note,
                    sortOrder = idx,
                )
            )
            FinanceUnexecutedSummary(saved.id, saved.content, saved.amount, saved.executedDate, saved.note, saved.sortOrder)
        }

        return FinanceReportDetail(
            id = report.id,
            year = report.year, month = report.month, week = report.week,
            incomeTotal = report.incomeTotal, expenseTotal = report.expenseTotal, balance = report.balance,
            sourceFilename = report.sourceFilename, uploadedBy = report.uploadedBy,
            checksumMismatch = report.checksumMismatch,
            formIncomeTotal = report.formIncomeTotal,
            formExpenseTotal = report.formExpenseTotal,
            lines = lines, unexecutedItems = unexecutedItems, createdAt = report.createdAt,
        )
    }

    @Transactional(readOnly = true)
    fun list(year: Int?, month: Int?, page: Int, size: Int): FinanceReportPage {
        val p = reportRepo.search(year, month, PageRequest.of(page, size))
        return FinanceReportPage(
            items = p.content.map { it.toSummary() },
            total = p.totalElements,
            hasNext = p.hasNext(),
        )
    }

    @Transactional(readOnly = true)
    fun get(id: Long): FinanceReportDetail {
        val report = reportRepo.findById(id).orElseThrow { NoSuchElementException("보고서를 찾을 수 없습니다: $id") }
        val categories = categoryRepo.findAll().associateBy { it.id }
        val lines = lineRepo.findAllByReportId(id).mapNotNull { line ->
            val cat = categories[line.categoryId] ?: return@mapNotNull null
            FinanceLineSummary(cat.id, cat.direction, cat.major, cat.minor, line.amount, line.detail)
        }
        val unexecuted = unexecutedRepo.findAllByReportIdOrderBySortOrder(id).map {
            FinanceUnexecutedSummary(it.id, it.content, it.amount, it.executedDate, it.note, it.sortOrder)
        }
        return FinanceReportDetail(
            id = report.id,
            year = report.year, month = report.month, week = report.week,
            incomeTotal = report.incomeTotal, expenseTotal = report.expenseTotal, balance = report.balance,
            sourceFilename = report.sourceFilename, uploadedBy = report.uploadedBy,
            checksumMismatch = report.checksumMismatch,
            formIncomeTotal = report.formIncomeTotal,
            formExpenseTotal = report.formExpenseTotal,
            lines = lines, unexecutedItems = unexecuted, createdAt = report.createdAt,
        )
    }

    fun delete(id: Long) {
        reportRepo.findById(id).orElseThrow { NoSuchElementException("보고서를 찾을 수 없습니다: $id") }
        reportRepo.deleteById(id)
    }

    private fun FinanceReport.toSummary() = FinanceReportSummary(
        id, year, month, week, incomeTotal, expenseTotal, balance, sourceFilename, uploadedBy, checksumMismatch, createdAt,
    )
}
