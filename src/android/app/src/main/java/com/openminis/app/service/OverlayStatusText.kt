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
     * Flyme / OEM status-bar lyrics marquee right-to-left when TITLE is
     * wider than the chip. Repeat the current snippet with a gap so a short
     * tool title still scrolls instead of sitting still or jumping lines.
     */
    fun marqueeLyric(text: String, minLen: Int = 56): String {
        val core = text.replace('\n', ' ').trim()
        if (core.isEmpty()) return core
        val gap = "          "
        val looped = buildString {
            append(core)
            while (length < minLen) append(gap).append(core)
            if (length == core.length) append(gap).append(core)
        }
        return if (looped.length <= 240) looped else looped.take(240)
    }

    fun shouldReplaceLyric(previous: String?, next: String): Boolean = previous != next
}
