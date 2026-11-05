package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PeriodReviewTest {
    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)
    private val nov1 = d(11, 1)
    private val nov30 = d(11, 30)

    @Test
    fun `a month counts only its own days - up to today`() {
        val inputs = PeriodInputs(
            spends = listOf(
                Txn("1", "Rent", "Rent", 30_000_00, d(11, 1), planned = false),
                Txn("2", "Toit", "Dining", 1_850_00, d(11, 2), planned = false),
                Txn("3", "Goa", "Travel", 15_000_00, d(11, 17), planned = true),
                Txn("4", "Myntra", "Shopping", 2_199_00, d(10, 30), planned = false),
            ),
            finished = listOf(
                FinishedThing("Dune", "Books", d(11, 3)),
                FinishedThing("Sapiens", "Books", d(11, 4)),
                FinishedThing("Oppenheimer", "Watch together", d(11, 2)),
                FinishedThing("Old book", "Books", d(10, 9)),
            ),
            activities = listOf(d(11, 2) to 30, d(11, 2) to 20, d(11, 4) to 45),
            diary = listOf(d(11, 1) to "🙂", d(11, 2) to "🙂", d(11, 3) to null, d(11, 4) to "😄"),
            tasksDone = listOf(d(11, 3), d(10, 31)),
        )
        val r = periodReview(nov1, nov30, d(11, 5), inputs)
        assertEquals(31_850_00L, r.spentMinor)
        assertEquals("Rent" to 30_000_00L, r.topSpends.first())
        assertEquals(FinishedGroup("📚", "Read", listOf("Dune", "Sapiens")), r.finished.first())
        assertEquals("Watched", r.finished[1].verb)
        assertEquals(95, r.activeMinutes)
        assertEquals(2, r.activeDays)
        assertEquals(4, r.diaryDays)
        assertEquals("🙂" to 2, r.moods.first())
        assertEquals(1, r.tasksDone)
        assertEquals(5, r.days)
    }

    @Test
    fun `a year adds up its months and shows spend month by month`() {
        val spends = listOf(
            Txn("1", "A", "Rent", 100_00, d(1, 5), planned = false),
            Txn("2", "B", "Rent", 300_00, d(3, 5), planned = false),
            Txn("3", "C", "Dining", 50_00, d(3, 9), planned = false),
        )
        val kitchen = kitchenMonths(listOf(KitchenBuy("Milk", d(3, 1)), KitchenBuy("Rice", d(11, 2))), emptyList(), emptyList(), null, d(11, 5), months = 12)
        val r = periodReview(d(1, 1), d(12, 31), d(11, 5), PeriodInputs(spends = spends, kitchen = kitchen))
        assertEquals(listOf(YearMonth.of(2026, 1) to 100_00L, YearMonth.of(2026, 3) to 350_00L), r.spendByMonth)
        assertEquals(2, r.bought)
        assertEquals(null, r.groceriesMinor)
    }

    @Test
    fun `no money access means no money in the story`() {
        val r = periodReview(nov1, nov30, d(11, 5), PeriodInputs(meals = listOf(d(11, 2))))
        assertEquals(null, r.spentMinor)
        assertTrue(r.topSpends.isEmpty())
        assertEquals(1, r.mealsCooked)
    }
}

class PlanMonthsTest {
    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)

    @Test
    fun `plans month by month - and added up for the year`() {
        val months = planMonths(
            tasks = listOf("Pay rent" to d(11, 1), "Fix tap" to d(10, 4), "Call plumber" to d(10, 2)),
            finished = listOf(FinishedThing("Dune", "Books", d(10, 9)), FinishedThing("Sapiens", "Books", d(11, 3)), FinishedThing("Goa", "Travel", d(10, 20))),
            goals = listOf("File taxes" to d(10, 30)),
            today = d(11, 5),
        )
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)), months.map { it.month })
        val oct = months[1]
        assertEquals(listOf("Call plumber", "Fix tap"), oct.tasks)
        assertEquals(2, oct.finishedCount)
        assertEquals(listOf("File taxes"), oct.goals)
        val year = planYears(months).single()
        assertEquals(3, year.tasks)
        assertEquals(FinishedGroup("📚", "Read", listOf("Dune", "Sapiens")), year.finished.first())
    }
}
