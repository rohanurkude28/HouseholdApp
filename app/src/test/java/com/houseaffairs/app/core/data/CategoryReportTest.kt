package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CategoryReportTest {
    private val today = LocalDate.of(2026, 11, 11)
    private fun t(id: String, title: String, amount: Long, date: LocalDate, cat: String = "Groceries", planned: Boolean = false) = Txn(id, title, cat, amount, date, planned)

    @Test
    fun `where grocery money went - by month and by shop`() {
        val r = categoryReport(
            listOf(
                t("1", "DMart", 1_200, today.minusDays(1)), t("2", "dmart ", 800, LocalDate.of(2026, 10, 3)), t("3", "Blinkit", 300, today),
                t("4", "Uber", 500, today, cat = "Transport"), t("5", "Big shop", 5_000, today.plusDays(5), planned = true), t("6", "DMart", 900, LocalDate.of(2025, 12, 1)),
            ),
            "Groceries", today,
        )
        assertEquals(1_500L, r.thisMonthMinor)
        assertEquals(2_300L, r.thisYearMinor)
        assertEquals(Triple("DMart", 3, 2_900L), r.places.first())
        assertEquals(YearMonth.of(2026, 11) to 1_500L, r.months.last())
        assertEquals(12, r.months.size)
        assertEquals("3", r.items.first().id)
    }
}
