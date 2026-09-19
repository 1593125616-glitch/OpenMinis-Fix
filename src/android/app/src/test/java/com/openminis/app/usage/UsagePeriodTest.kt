package com.openminis.app.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class UsagePeriodTest {
    private val tz: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

    private fun at(year: Int, month1: Int, day: Int, hour: Int = 12, minute: Int = 0): Long {
        val c = Calendar.getInstance(tz)
        c.clear()
        c.set(Calendar.YEAR, year)
        c.set(Calendar.MONTH, month1 - 1)
        c.set(Calendar.DAY_OF_MONTH, day)
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, minute)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    @Test
    fun `parseJson reads cache aliases`() {
        val t = UsagePeriod.parseJson(
            """{"inputTokens":10,"outputTokens":2,"cacheReadInputTokens":4,"cacheCreationInputTokens":1}""",
        )!!
        assertEquals(10, t.inputTokens)
        assertEquals(2, t.outputTokens)
        assertEquals(4, t.cacheReadTokens)
        assertEquals(1, t.cacheCreationTokens)
        assertEquals(15, t.totalInput)
        assertEquals(11, t.billedInput)
    }

    @Test
    fun `today week month split on Saturday in Shanghai`() {
        // 2026-09-19 is Saturday. Week starts Monday 2026-09-14.
        val now = at(2026, 9, 19, 15, 30)
        val rows = listOf(
            at(2026, 9, 19, 9) to TokenTotals(inputTokens = 100, outputTokens = 10),
            at(2026, 9, 14, 0, 1) to TokenTotals(inputTokens = 20),
            at(2026, 9, 13, 23, 59) to TokenTotals(inputTokens = 7),
            at(2026, 9, 1, 0) to TokenTotals(inputTokens = 3),
            at(2026, 8, 31, 23, 59) to TokenTotals(inputTokens = 50),
        )
        val snap = UsagePeriod.buildSnapshot(rows, now, tz)
        assertEquals(100, snap.today.inputTokens)
        assertEquals(120, snap.week.inputTokens) // today + Monday, not Sunday
        assertEquals(130, snap.month.inputTokens) // Sep 1 + week + today, not Aug 31
        assertEquals(180, snap.all.inputTokens)
        assertEquals("2026-09-14", UsagePeriod.dayKey(UsagePeriod.startOfWeekMonday(now, tz), tz))
        assertEquals("2026-09-01", UsagePeriod.dayKey(UsagePeriod.startOfMonth(now, tz), tz))
    }

    @Test
    fun `day key follows local timezone around midnight`() {
        val now = at(2026, 9, 19, 0, 30)
        val shanghaiToday = at(2026, 9, 19, 2)
        val shanghaiYesterday = at(2026, 9, 18, 23)
        val snap = UsagePeriod.buildSnapshot(
            listOf(
                shanghaiToday to TokenTotals(inputTokens = 8),
                shanghaiYesterday to TokenTotals(inputTokens = 1),
            ),
            now,
            tz,
        )
        assertEquals(8, snap.today.inputTokens)
        assertEquals(1L, snap.byDay["2026-09-18"]?.inputTokens)
    }

    @Test
    fun `last 14 days fills zeros and drops older`() {
        val now = at(2026, 9, 19, 12)
        val snap = UsagePeriod.buildSnapshot(
            listOf(
                at(2026, 9, 19) to TokenTotals(outputTokens = 5),
                at(2026, 9, 6) to TokenTotals(outputTokens = 9),
                at(2026, 9, 5) to TokenTotals(outputTokens = 99),
            ),
            now,
            tz,
        )
        val days = UsagePeriod.chartDays(snap, UsageRange.ALL, now, tz, allLookbackDays = 14)
        assertEquals(14, days.size)
        assertEquals("2026-09-19", days.first().dayKey)
        assertEquals("2026-09-06", days.last().dayKey)
        assertEquals(5, days.first().totals.outputTokens)
        assertEquals(9, days.last().totals.outputTokens)
        assertTrue(days.none { it.dayKey == "2026-09-05" })
        assertEquals(0, days[1].totals.outputTokens)
    }

    @Test
    fun `rangeStart all is unbounded`() {
        assertNull(UsagePeriod.rangeStartMs(UsageRange.ALL, 1L, tz))
    }

    @Test
    fun `estimate does not double-count cache`() {
        val t = TokenTotals(inputTokens = 1_000_000, cacheReadTokens = 1_000_000)
        assertEquals(1_000_000, t.billedInput)
        assertEquals(3.3, t.estimatedUsd, 1e-9)
    }
}
