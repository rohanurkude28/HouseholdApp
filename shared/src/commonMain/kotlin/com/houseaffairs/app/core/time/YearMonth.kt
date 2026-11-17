package com.houseaffairs.app.core.time

/** A month of a year, like java.time.YearMonth, the same on both phones. */
data class YearMonth(val year: Int, val monthValue: Int) : Comparable<YearMonth> {
    init { require(monthValue in 1..12) { "Month $monthValue" } }

    fun plusMonths(months: Long): YearMonth = addMonths(year, monthValue, months.toInt()).let { (y, m) -> YearMonth(y, m) }
    fun minusMonths(months: Long): YearMonth = plusMonths(-months)
    fun plusMonths(months: Int): YearMonth = plusMonths(months.toLong())
    fun minusMonths(months: Int): YearMonth = plusMonths(-months.toLong())
    fun atDay(day: Int): LocalDate = localDate(year, monthValue, day)
    fun atEndOfMonth(): LocalDate = lastDayOfMonth(year, monthValue)
    fun lengthOfMonth(): Int = lengthOfMonth(year, monthValue)
    fun isBefore(other: YearMonth): Boolean = this < other
    fun isAfter(other: YearMonth): Boolean = this > other

    /** "MMMM", "MMM yyyy"…, as [LocalDate.format]. */
    fun format(pattern: String): String = atDay(1).format(pattern)

    override fun compareTo(other: YearMonth): Int = (year * 12 + monthValue).compareTo(other.year * 12 + other.monthValue)

    companion object {
        fun of(year: Int, month: Int) = YearMonth(year, month)
        fun from(date: LocalDate) = YearMonth(date.year, date.monthValue)
    }
}
