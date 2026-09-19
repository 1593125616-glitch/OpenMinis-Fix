package com.openminis.app.usage

/**
 * Rough USD estimate from token counts. Numbers are order-of-magnitude
 * defaults (not live pricing) so the dashboard can show a daily burn.
 */
data class UsageDay(
    val day: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long,
    val estimatedUsd: Double,
)

object UsageCostEstimator {
    /** Default: ~$3 / M input, $15 / M output (Claude-ish mid). */
    const val INPUT_PER_MILLION = 3.0
    const val OUTPUT_PER_MILLION = 15.0
    const val CACHE_READ_PER_MILLION = 0.30

    fun estimateUsd(input: Long, output: Long, cacheRead: Long = 0): Double {
        return input * INPUT_PER_MILLION / 1_000_000.0 +
            output * OUTPUT_PER_MILLION / 1_000_000.0 +
            cacheRead * CACHE_READ_PER_MILLION / 1_000_000.0
    }

    fun formatUsd(usd: Double): String =
        "$" + String.format(java.util.Locale.US, "%.4f", usd)

    fun rollupDays(days: List<UsageDay>): UsageDay {
        return UsageDay(
            day = "all",
            inputTokens = days.sumOf { it.inputTokens },
            outputTokens = days.sumOf { it.outputTokens },
            cacheReadTokens = days.sumOf { it.cacheReadTokens },
            estimatedUsd = days.sumOf { it.estimatedUsd },
        )
    }
}
