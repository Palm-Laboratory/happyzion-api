package org.happyzion.api.finance.application

import java.time.DayOfWeek
import java.time.LocalDate

/** 재정 보고서의 주(week) 번호 = 그 달의 N번째 주일(일요일) 기준. */
object FinancePeriodUtil {

    /** 해당 연·월에 포함된 주일(일요일) 수 — 보고서 week 번호의 상한. */
    fun sundayCountInMonth(year: Int, month: Int): Int {
        require(month in 1..12) { "월은 1~12 사이여야 합니다 (입력: ${month}월)" }
        val firstDay = LocalDate.of(year, month, 1)
        val lastDay = firstDay.lengthOfMonth()
        val firstSunday = (1..7).first { LocalDate.of(year, month, it).dayOfWeek == DayOfWeek.SUNDAY }
        return generateSequence(firstSunday) { it + 7 }.takeWhile { it <= lastDay }.count()
    }
}
