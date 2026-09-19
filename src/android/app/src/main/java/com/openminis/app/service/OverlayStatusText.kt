package com.openminis.app.service

/**
 * One-line status shared by the floating capsule and the system status bar
 * (Flyme lyrics / MediaSession / promoted chip).
 *
 * The status bar must never marquee the whole turn from the first sentence —
 * only the current snippet the overlay would show.
 */
object OverlayStatusText {
    const val MAX_CHARS = 72

    fun collapse(fullText: String?): String? =
        fullText
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString(" ")
            ?.takeIf { it.isNotBlank() }

    /** Latest window, not the prefix — matches "current progress". */
    fun excerptTail(fullText: String?, maxChars: Int = MAX_CHARS): String? {
        val collapsed = collapse(fullText) ?: return null
        return clip(collapsed, maxChars)
    }

    fun clip(text: String, maxChars: Int = MAX_CHARS): String {
        val t = text.replace('\n', ' ').trim()
        if (t.length <= maxChars) return t
        return "…" + t.takeLast((maxChars - 1).coerceAtLeast(1)).trimStart()
    }

    fun glance(
        isRunning: Boolean,
        toolTitle: String?,
        toolName: String?,
        status: String,
        replyExcerpt: String?,
        fallback: String = "",
    ): String {
        val idle = status.equals("Idle", ignoreCase = true)
        if (isRunning) {
            toolTitle?.trim()?.takeIf { it.isNotEmpty() }?.let { return clip(it) }
            status.trim().takeIf { it.isNotEmpty() && !idle }?.let { return clip(it) }
            toolName?.trim()?.takeIf { it.isNotEmpty() }?.let { return clip(it) }
            return fallback.trim()
        }
        replyExcerpt?.trim()?.takeIf { it.isNotEmpty() }?.let { return clip(it) }
        toolTitle?.trim()?.takeIf { it.isNotEmpty() }?.let { return clip(it) }
        status.trim().takeIf { it.isNotEmpty() && !idle }?.let { return clip(it) }
        return fallback.trim()
    }

    /**
     * One status-bar line. Flyme / ColorOS "lyrics" chips draw 2–3 rows and
     * treat TITLE, DISPLAY_TITLE, and LYRICS as separate lines. Concatenating
     * the same snippet (or publishing it on all three keys) stacked three
     * identical rows that then popped vertically instead of marqueeing.
     *
     * Keep a single copy, cap it to one chip, and pad with ideographic
     * spaces so a short tool title is wider than the chip and the ROM can
     * RTL-scroll that one copy.
     */
    const val STATUS_BAR_MIN = 28
    const val STATUS_BAR_MAX = 36

    fun marqueeLyric(text: String, minWidth: Int = STATUS_BAR_MIN): String {
        val core = clip(text, STATUS_BAR_MAX)
        if (core.isEmpty()) return core
        if (core.length >= minWidth) return core
        return core + "\u3000".repeat(minWidth - core.length)
    }

    fun shouldReplaceLyric(previous: String?, next: String): Boolean = previous != next
}
