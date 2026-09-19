package com.openminis.app.memory

/**
 * Progressive-disclosure helpers for daily memory files.
 * - `<private>` / `<!-- private -->` blocks are stripped from agent search
 * - hits are cited as `file:Lline`
 */
object MemoryPrivacy {
    private val PRIVATE_BLOCK = Regex(
        """(?is)(?:<!--\s*private\s*-->|<private>)(.*?)(?:<!--\s*/private\s*-->|</private>)""",
    )

    fun stripPrivate(text: String): String {
        var s = PRIVATE_BLOCK.replace(text, "")
        // Unclosed private block: drop the rest of the file rather than leak it.
        s = s.replace(Regex("(?is)(?:<!--\\s*private\\s*-->|<private>).*$"), "")
        return s
    }

    fun isPrivateLine(line: String): Boolean {
        val t = line.lowercase()
        return t.contains("<private>") || t.contains("<!-- private")
    }

    fun cite(fileName: String, lineNumber1: Int, line: String): String {
        return "[$fileName:L$lineNumber1] ${line.trim()}"
    }
}
