package org.happyzion.api.finance.interfaces.api

import org.happyzion.api.common.config.UploadProperties
import org.happyzion.api.common.error.NotFoundException
import org.happyzion.api.common.security.AdminAuthRequired
import org.happyzion.api.finance.application.*
import org.happyzion.api.finance.interfaces.dto.*
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path

@AdminAuthRequired
@RestController
@RequestMapping("/api/v1/admin/finance")
class FinanceAdminController(
    private val reportService: FinanceReportService,
    private val statisticsService: FinanceStatisticsService,
    private val uploadProperties: UploadProperties,
) {
    /**
     * 재정보고서 엑셀 양식 다운로드.
     * 관리자가 서버 `<uploads-root>/finance/template.xlsx` 에 양식 파일을 올려두면 그대로 내려준다.
     */
    @GetMapping("/template")
    fun downloadTemplate(): ResponseEntity<Resource> {
        val templatePath: Path = Path.of(uploadProperties.rootPath)
            .resolve("finance")
            .resolve("template.xlsx")
            .normalize()
        val file = templatePath.toFile()
        if (!file.exists() || !file.isFile) {
            throw NotFoundException("등록된 재정보고서 양식이 없습니다. 서버에 양식 파일을 먼저 올려주세요.")
        }
        // 한글 파일명은 RFC 5987 `filename*` 로 내려야 브라우저가 올바르게 디코딩한다.
        // (Spring ContentDisposition 은 RFC 2047 encoded-word 로 출력해 브라우저가 못 푸는 문제가 있어 직접 구성)
        val downloadName = "시온재정_양식.xlsx"
        val encodedName = URLEncoder.encode(downloadName, StandardCharsets.UTF_8).replace("+", "%20")
        val contentDisposition = "attachment; filename=\"finance_template.xlsx\"; filename*=UTF-8''$encodedName"
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(FileSystemResource(file))
    }

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
