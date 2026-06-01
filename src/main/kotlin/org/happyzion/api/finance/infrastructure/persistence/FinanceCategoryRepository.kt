package org.happyzion.api.finance.infrastructure.persistence

import org.happyzion.api.finance.domain.FinanceCategory
import org.happyzion.api.finance.domain.FinanceDirection
import org.springframework.data.jpa.repository.JpaRepository

interface FinanceCategoryRepository : JpaRepository<FinanceCategory, Long> {
    fun findAllByActiveOrderBySortOrder(active: Boolean = true): List<FinanceCategory>
    fun findByDirectionAndMajorAndMinor(direction: FinanceDirection, major: String, minor: String): FinanceCategory?
}
