package com.openminis.app.a11y

/**
 * Compact accessibility snapshot lines: `@e12 [button] Submit (clickable)`
 * so the agent can `tap node` by ref instead of screenshot-clicking.
 */
data class A11yNode(
    val id: String,
    val role: String,
    val text: String,
    val clickable: Boolean,
    val editable: Boolean,
)

object A11ySnapshotFormatter {
    fun line(index: Int, node: A11yNode): String {
        val ref = "@e$index"
        val bits = buildList {
            add(node.role.ifBlank { "node" })
            if (node.clickable) add("clickable")
            if (node.editable) add("editable")
        }
        val label = node.text.replace('\n', ' ').trim().take(80)
        val quoted = if (label.isEmpty()) "" else " \"$label\""
        return "$ref [${bits.joinToString(" ")}] id=${node.id}$quoted"
    }

    fun format(nodes: List<A11yNode>, max: Int = 80): String {
        val take = nodes.take(max)
        val lines = take.mapIndexed { i, n -> line(i, n) }
        val extra = (nodes.size - take.size).coerceAtLeast(0)
        return buildString {
            appendLine("snapshot ${take.size} nodes. Tap with `android-a11y-cli tap node @eN` or the raw id.")
            lines.forEach { appendLine(it) }
            if (extra > 0) appendLine("… $extra more omitted")
        }.trimEnd()
    }

    /** `@e12` → index 12 */
    fun parseRef(token: String): Int? {
        val t = token.trim()
        if (!t.startsWith("@e")) return null
        return t.removePrefix("@e").toIntOrNull()
    }
}
