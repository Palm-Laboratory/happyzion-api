package org.happyzion.api.finance.infrastructure.persistence

import org.happyzion.api.finance.domain.FinanceReport
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface FinanceReportRepository : JpaRepository<FinanceReport, Long> {
    fun findByYearAndMonthAndWeek(year: Int, month: Int, week: Int): FinanceReport?

    @Query("""
        select r from FinanceReport r
        where (:year is null or r.year = :year)
          and (:month is null or r.month = :month)
        order by r.year desc, r.month desc, r.week desc
    """)
    fun search(year: Int?, month: Int?, pageable: Pageable): Page<FinanceReport>

    @Query("select r from FinanceReport r where r.year = :year order by r.month, r.week")
    fun findAllByYear(year: Int): List<FinanceReport>

    @Query("select r from FinanceReport r where r.year = :year and r.month = :month order by r.week")
    fun findAllByYearAndMonth(year: Int, month: Int): List<FinanceReport>

    @Query("select distinct r.year from FinanceReport r order by r.year")
    fun findDistinctYears(): List<Int>

    @Query("select coalesce(sum(r.incomeTotal), 0) from FinanceReport r")
    fun sumIncomeTotal(): Long

    @Query("select coalesce(sum(r.expenseTotal), 0) from FinanceReport r")
    fun sumExpenseTotal(): Long
}
