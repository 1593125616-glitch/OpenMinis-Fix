package com.openminis.app.channel

/**
 * Telegram sendMessage caps text at 4096 UTF-16 code units. Split on
 * newline boundaries when possible so a long agent reply still arrives.
 */
object TelegramMessageSplitter {
    const val MAX_CHARS = 4096

    fun split(text: String, maxChars: Int = MAX_CHARS): List<String> {
        if (text.isEmpty()) return emptyList()
        if (text.length <= maxChars) return listOf(text)
        val out = ArrayList<String>()
        var remaining = text
        while (remaining.length > maxChars) {
            val window = remaining.substring(0, maxChars)
            val nl = window.lastIndexOf('\n')
            val cut = if (nl >= maxChars / 2) nl + 1 else maxChars
            out.add(remaining.substring(0, cut).trimEnd())
            remaining = remaining.substring(cut).trimStart()
        }
        if (remaining.isNotEmpty()) out.add(remaining)
        return out
    }
}
