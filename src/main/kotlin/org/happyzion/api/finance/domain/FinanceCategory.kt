package org.happyzion.api.finance.domain

import jakarta.persistence.*
import java.time.OffsetDateTime

enum class FinanceDirection { INCOME, EXPENSE }

@Entity
@Table(name = "finance_category")
class FinanceCategory(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val direction: FinanceDirection,

    @Column(nullable = false, length = 60)
    val major: String,

    @Column(nullable = false, length = 60)
    val minor: String,

    @Column(name = "sort_order", nullable = false)
    val sortOrder: Int = 0,

    @Column(nullable = false)
    val active: Boolean = true,
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
}
