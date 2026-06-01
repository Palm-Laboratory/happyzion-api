package org.happyzion.api.finance.application

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Component
import java.io.InputStream

/** 기획 §4 셀 맵 — (대분류, 소분류, 셀주소) */
private val INCOME_CELL_MAP = listOf(
    Triple("십일조",   "십일조",   "C5"),
    Triple("헌금",     "감사헌금", "C7"),
    Triple("헌금",     "주정헌금", "C8"),
    Triple("헌금",     "목장헌금", "C9"),
    Triple("헌금",     "절기감사", "C10"),
    Triple("특별헌금", "선교헌금", "C12"),
    Triple("특별헌금", "건축헌금", "C13"),
    Triple("특별헌금", "꽃헌금",   "C14"),
    Triple("특별헌금", "목적헌금", "C15"),
    Triple("찬조헌금", "행사찬조", "C17"),
)

private val EXPENSE_CELL_MAP = listOf(
    Triple("목회자",    "십일조",           "G5"),
    Triple("목회자",    "헌금",             "G6"),
    Triple("목회자",    "은급비",           "G7"),
    Triple("목회자",    "연금",             "G8"),
    Triple("목회자",    "실손보험",         "G9"),
    Triple("목회자",    "은퇴비적립",       "G10"),
    Triple("목회자",    "자녀교육비",       "G11"),
    Triple("선교비",    "하늘보화",         "G13"),
    Triple("선교비",    "교회선교",         "G14"),
    Triple("선교비",    "성도선교",         "G15"),
    Triple("선교비",    "해외선교",         "G16"),
    Triple("선교비적립","해외선교적립",     "G18"),
    Triple("교회유지",  "세스코",           "G20"),
    Triple("교회유지",  "화재보험",         "G21"),
    Triple("교회유지",  "건물유지소모품비", "G22"),
    Triple("특별헌금",  "선교헌금",         "G25"),
    Triple("특별헌금",  "건축헌금",         "G26"),
    Triple("특별헌금",  "꽃헌금",           "G27"),
    Triple("특별헌금",  "목적헌금",         "G28"),
    Triple("행사비",    "교회행사",         "G30"),
    Triple("행사비",    "기도원",           "G31"),
    Triple("행사비",    "기타",             "G32"),
    Triple("찬조헌금",  "행사찬조",         "G34"),
    Triple("고정자산",  "교회비품",         "G38"),
    Triple("카드성물",  "농협",             "G41"),
    Triple("카드성물",  "삼성",             "G42"),
    Triple("카드성물",  "신한",             "G43"),
    Triple("경비",      "주일식사비",       "G51"),
    Triple("경비",      "주간부식비",       "G52"),
    Triple("경비",      "전도활동비",       "G54"),
    Triple("경비",      "공과금",           "G55"),
    Triple("경비",      "세금",             "G57"),
    Triple("경비",      "노회비",           "G58"),
    Triple("경비",      "통신비",           "G59"),
    Triple("경비",      "의료비",           "G60"),
    Triple("경비",      "차량유지비",       "G61"),
    Triple("경비",      "소모품비",         "G63"),
    Triple("경비",      "사무용품비",       "G65"),
    Triple("경비",      "선물비",           "G67"),
    Triple("경비",      "심방비",           "G68"),
    Triple("경비",      "경조비",           "G69"),
)

/** 미집행 품목 데이터행 (엑셀 1-based row index) */
private val UNEXECUTED_ROWS = listOf(84, 86, 88, 90, 92, 94)

/** A1 기간 텍스트에서 연/월/주 추출 — 예: "2026년 5월 3주" */
private val PERIOD_PATTERN = Regex("""(\d{4})\s*년\s*(\d{1,2})\s*월\s*(\d{1,2})\s*주""")

data class ParsedPeriod(val year: Int, val month: Int, val week: Int)
data class ParsedLine(val major: String, val minor: String, val amount: Long, val isIncome: Boolean)
data class ParsedUnexecutedItem(
    val content: String,
    val amount: Long,
    val executedDate: String?,
    val note: String?,
)

data class FinanceParseResult(
    val sourceFilename: String,
    val period: ParsedPeriod?,
    val periodSourceText: String?,
    val incomeLines: List<ParsedLine>,
    val expenseLines: List<ParsedLine>,
    val unexecutedItems: List<ParsedUnexecutedItem>,
    val incomeTotal: Long,
    val expenseTotal: Long,
    val balance: Long,
    /** 양식 합계셀(C70) 캐시값. 교차검증용. */
    val formIncomeTotal: Long?,
    val formExpenseTotal: Long?,
    val checksumMismatch: Boolean,
)

@Component
class FinanceExcelParser {

    fun parse(input: InputStream, filename: String): FinanceParseResult {
        val wb = XSSFWorkbook(input)
        val ws = wb.getSheetAt(0)

        // 1) 기간 추출 (A1)
        val periodText = ws.getRow(0)?.getCell(0)?.stringCellValue?.trim()
        val period = periodText?.let { PERIOD_PATTERN.find(it) }?.let {
            ParsedPeriod(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
        }

        // 2) 수입 라인
        val incomeLines = INCOME_CELL_MAP.map { (major, minor, addr) ->
            val amount = numericValue(ws, addr)
            ParsedLine(major, minor, amount, isIncome = true)
        }

        // 3) 지출 라인
        val expenseLines = EXPENSE_CELL_MAP.map { (major, minor, addr) ->
            val amount = numericValue(ws, addr)
            ParsedLine(major, minor, amount, isIncome = false)
        }

        // 4) 합계 직접 합산
        val incomeTotal = incomeLines.sumOf { it.amount }
        val expenseTotal = expenseLines.sumOf { it.amount }

        // 5) 양식 합계셀 교차검증 (C70 = row 69, G70 = row 69)
        val formIncome = numericValueOrNull(ws, "C70")
        val formExpense = numericValueOrNull(ws, "G70")
        val checksumMismatch = (formIncome != null && formIncome != incomeTotal) ||
                (formExpense != null && formExpense != expenseTotal)

        // 6) 미집행 품목 (B=content, D=amount, G=executedDate, I=note / 엑셀 0-based: row-1)
        val unexecutedItems = UNEXECUTED_ROWS.mapNotNull { rowNum ->
            val row = ws.getRow(rowNum - 1) ?: return@mapNotNull null
            val content = row.getCell(1)?.stringCellValue?.trim()
                .takeIf { !it.isNullOrBlank() } ?: return@mapNotNull null
            val amount = numericValueFromCell(row.getCell(3))
            val executedDate = row.getCell(6)?.let { cell ->
                runCatching { cell.localDateTimeCellValue?.toLocalDate()?.toString() }.getOrNull()
                    ?: cell.stringCellValue?.trim()?.takeIf { it.isNotBlank() }
            }
            val note = row.getCell(8)?.stringCellValue?.trim()?.takeIf { it.isNotBlank() }
            ParsedUnexecutedItem(content, amount, executedDate, note)
        }

        wb.close()

        return FinanceParseResult(
            sourceFilename = filename,
            period = period,
            periodSourceText = periodText?.takeIf { it.isNotBlank() },
            incomeLines = incomeLines,
            expenseLines = expenseLines,
            unexecutedItems = unexecutedItems,
            incomeTotal = incomeTotal,
            expenseTotal = expenseTotal,
            balance = incomeTotal - expenseTotal,
            formIncomeTotal = formIncome,
            formExpenseTotal = formExpense,
            checksumMismatch = checksumMismatch,
        )
    }

    /** 셀 주소(예: "C5") → 0-based row/col 변환 후 숫자 읽기 */
    private fun numericValue(ws: org.apache.poi.ss.usermodel.Sheet, addr: String): Long {
        return numericValueOrNull(ws, addr) ?: 0L
    }

    private fun numericValueOrNull(ws: org.apache.poi.ss.usermodel.Sheet, addr: String): Long? {
        val col = addr[0] - 'A'          // 'C' -> 2
        val row = addr.substring(1).toInt() - 1  // "5" -> 4 (0-based)
        val cell = ws.getRow(row)?.getCell(col) ?: return null
        return numericValueFromCell(cell)
    }

    private fun numericValueFromCell(cell: org.apache.poi.ss.usermodel.Cell?): Long {
        cell ?: return 0L
        return when (cell.cellType) {
            org.apache.poi.ss.usermodel.CellType.NUMERIC -> cell.numericCellValue.toLong()
            org.apache.poi.ss.usermodel.CellType.FORMULA -> runCatching { cell.numericCellValue.toLong() }.getOrDefault(0L)
            else -> 0L
        }
    }
}
