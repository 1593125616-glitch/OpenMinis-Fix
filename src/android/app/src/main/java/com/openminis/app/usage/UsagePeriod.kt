package com.openminis.app.usage

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Calendar windows for the usage dashboard. Week is Monday–Sunday in [tz]. */
enum class UsageRange { TODAY, WEEK, MONTH, ALL }

data class TokenTotals(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val cacheCreationTokens: Long = 0,
) {
    val totalInput: Long get() = inputTokens + cacheReadTokens + cacheCreationTokens
    val billedInput: Long get() = (totalInput - cacheReadTokens).coerceAtLeast(0)
    val chartTokens: Long get() = totalInput + outputTokens
    val estimatedUsd: Double
        get() = UsageCostEstimator.estimateUsd(billedInput, outputTokens, cacheReadTokens)

    operator fun plus(other: TokenTotals) = TokenTotals(
        inputTokens = inputTokens + other.inputTokens,
        outputTokens = outputTokens + other.outputTokens,
        cacheReadTokens = cacheReadTokens + other.cacheReadTokens,
        cacheCreationTokens = cacheCreationTokens + other.cacheCreationTokens,
    )
}

data class UsageDayRow(
    val dayKey: String,
    val totals: TokenTotals,
)

data class UsageSnapshot(
    val today: TokenTotals,
    val week: TokenTotals,
    val month: TokenTotals,
    val all: TokenTotals,
    val byDay: Map<String, TokenTotals>,
)

object UsagePeriod {
    fun parseJson(tokenUsage: String): TokenTotals? {
        val usage = try {
            JSONObject(tokenUsage)
        } catch (_: Exception) {
            return null
        }
        return TokenTotals(
            inputTokens = usage.optLong("inputTokens", 0),
            outputTokens = usage.optLong("outputTokens", 0),
            cacheCreationTokens = usage.optLong(
                "cacheCreationTokens",
                usage.optLong("cacheCreationInputTokens", 0),
            ),
            cacheReadTokens = usage.optLong(
                "cacheReadTokens",
                usage.optLong("cacheReadInputTokens", 0),
            ),
        )
    }

    fun dayKey(epochMs: Long, tz: TimeZone): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = tz
        return fmt.format(Date(epochMs))
    }

    fun shortDayLabel(dayKey: String): String =
        if (dayKey.length >= 10) dayKey.substring(5) else dayKey

    fun startOfDay(epochMs: Long, tz: TimeZone): Long {
        val c = Calendar.getInstance(tz)
        c.timeInMillis = epochMs
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun startOfWeekMonday(epochMs: Long, tz: TimeZone): Long {
        val c = Calendar.getInstance(tz)
        c.timeInMillis = startOfDay(epochMs, tz)
        val daysFromMonday = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
        c.add(Calendar.DATE, -daysFromMonday)
        return c.timeInMillis
    }

    fun startOfMonth(epochMs: Long, tz: TimeZone): Long {
        val c = Calendar.getInstance(tz)
        c.timeInMillis = startOfDay(epochMs, tz)
        c.set(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    fun rangeStartMs(range: UsageRange, nowMs: Long, tz: TimeZone): Long? = when (range) {
        UsageRange.TODAY -> startOfDay(nowMs, tz)
        UsageRange.WEEK -> startOfWeekMonday(nowMs, tz)
        UsageRange.MONTH -> startOfMonth(nowMs, tz)
        UsageRange.ALL -> null
    }

    fun buildSnapshot(
        rows: List<Pair<Long, TokenTotals>>,
        nowMs: Long,
        tz: TimeZone,
    ): UsageSnapshot {
        val todayStart = startOfDay(nowMs, tz)
        val weekStart = startOfWeekMonday(nowMs, tz)
        val monthStart = startOfMonth(nowMs, tz)
        var today = TokenTotals()
        var week = TokenTotals()
        var month = TokenTotals()
        var all = TokenTotals()
        val byDay = linkedMapOf<String, TokenTotals>()
        for ((createdAt, totals) in rows) {
            all += totals
            if (createdAt >= monthStart) month += totals
            if (createdAt >= weekStart) week += totals
            if (createdAt >= todayStart) today += totals
            val key = dayKey(createdAt, tz)
            byDay[key] = (byDay[key] ?: TokenTotals()) + totals
        }
        return UsageSnapshot(today, week, month, all, byDay)
    }

    fun totalsFor(snapshot: UsageSnapshot, range: UsageRange): TokenTotals = when (range) {
        UsageRange.TODAY -> snapshot.today
        UsageRange.WEEK -> snapshot.week
        UsageRange.MONTH -> snapshot.month
        UsageRange.ALL -> snapshot.all
    }

    /**
     * Newest-first local days. [ALL] shows a trailing window rather than
     * every historical day so the chart stays readable.
     */
    fun chartDays(
        snapshot: UsageSnapshot,
        range: UsageRange,
        nowMs: Long,
        tz: TimeZone,
        allLookbackDays: Int = 14,
    ): List<UsageDayRow> {
        val end = startOfDay(nowMs, tz)
        val start = when (range) {
            UsageRange.TODAY -> end
            UsageRange.WEEK -> startOfWeekMonday(nowMs, tz)
            UsageRange.MONTH -> startOfMonth(nowMs, tz)
            UsageRange.ALL -> {
                val c = Calendar.getInstance(tz)
                c.timeInMillis = end
                c.add(Calendar.DATE, -(allLookbackDays - 1).coerceAtLeast(0))
                c.timeInMillis
            }
        }
        return daysNewestFirst(snapshot.byDay, start, end, tz)
    }

    fun daysNewestFirst(
        byDay: Map<String, TokenTotals>,
        startMs: Long,
        endMs: Long,
        tz: TimeZone,
    ): List<UsageDayRow> {
        val start = startOfDay(startMs, tz)
        val end = startOfDay(endMs, tz)
        if (end < start) return emptyList()
        val c = Calendar.getInstance(tz)
        c.timeInMillis = end
        val out = ArrayList<UsageDayRow>()
        var guard = 0
        while (c.timeInMillis >= start && guard < 400) {
            val key = dayKey(c.timeInMillis, tz)
            out += UsageDayRow(key, byDay[key] ?: TokenTotals())
            c.add(Calendar.DATE, -1)
            guard++
        }
        return out
    }
}
