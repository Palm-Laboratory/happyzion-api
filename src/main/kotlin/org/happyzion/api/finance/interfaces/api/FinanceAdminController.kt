package org.happyzion.api.finance.interfaces.api

import org.happyzion.api.common.security.AdminAuthRequired
import org.happyzion.api.finance.application.*
import org.happyzion.api.finance.interfaces.dto.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@AdminAuthRequired
@RestController
@RequestMapping("/api/v1/admin/finance")
class FinanceAdminController(
    private val reportService: FinanceReportService,
    private val statisticsService: FinanceStatisticsService,
) {
    /** 엑셀 업로드 → 파싱 미리보기 (DB 저장 없음) */
    @PostMapping("/reports/preview")
    fun preview(
        @RequestPart("file") file: MultipartFile,
    ): FinancePreviewResponse {
        val result = reportService.preview(file)
        return result.parseResult.toPreviewResponse(result.isDuplicate)
    }

    /** 미리보기 확인 후 확정 저장 (multipart: file + year/month/week) */
    @PostMapping("/reports")
    fun save(
        @RequestAttribute("adminAccountId") actorId: Long,
        @RequestPart("file") file: MultipartFile,
        @RequestPart("year") year: String,
        @RequestPart("month") month: String,
        @RequestPart("week") week: String,
    ): FinanceReportDetailResponse {
        val parseResult = reportService.preview(file).parseResult
        return reportService.save(parseResult, year.toInt(), month.toInt(), week.toInt(), actorId)
            .toDetailResponse()
    }

    /** 보고서 목록 */
    @GetMapping("/reports")
    fun list(
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): FinanceReportPageResponse {
        val result = reportService.list(year, month, page, size)
        return FinanceReportPageResponse(
            items = result.items.map { it.toResponse() },
            total = result.total,
            hasNext = result.hasNext,
        )
    }

    /** 보고서 상세 */
    @GetMapping("/reports/{id}")
    fun get(@PathVariable id: Long): FinanceReportDetailResponse =
        reportService.get(id).toDetailResponse()

    /** 보고서 삭제 */
    @DeleteMapping("/reports/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        reportService.delete(id)
        return ResponseEntity.noContent().build()
    }

    /** 누적 잔액 */
    @GetMapping("/balance")
    fun balance(): Map<String, Long> {
        val income = reportService.totalIncomeSum()
        val expense = reportService.totalExpenseSum()
        return mapOf("incomeTotal" to income, "expenseTotal" to expense, "balance" to income - expense)
    }

    /** 통계 */
    @GetMapping("/statistics")
    fun statistics(
        @RequestParam(defaultValue = "MONTH") granularity: String,
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?,
    ): FinanceStatResponse {
        val g = StatGranularity.valueOf(granularity)
        return statisticsService.statistics(g, year, month).toResponse()
    }
}
