package com.openminis.app.tools

import com.openminis.app.data.model.AgentToolDefinition
import com.openminis.app.data.model.AgentToolParam
import org.json.JSONObject

/**
 * Session-local HTML canvas. The chat UI renders the HTML in a sandboxed
 * WebView (JS off). Marker lets the bubble detector find it.
 */
object CanvasTool {
    const val NAME = "canvas"
    const val MARKER = "<!--minis-canvas-->"

    fun definition(): AgentToolDefinition = AgentToolDefinition(
        name = NAME,
        description = "Render a small HTML canvas in this chat (charts, tables, checklists). " +
            "JavaScript is disabled. Keep HTML under 50KB. Do not include remote scripts.",
        parameters = mapOf(
            "tool_title" to AgentToolParam("string", "5-10 word summary of the canvas."),
            "html" to AgentToolParam("string", "HTML body or full document to render."),
            "title" to AgentToolParam("string", "Optional heading shown above the canvas."),
        ),
        required = listOf("tool_title", "html"),
        propertyOrdering = listOf("tool_title", "html", "title"),
    )

    fun execute(argsJson: String): ToolExecutionResult {
        val args = JSONObject(argsJson)
        val html = args.optString("html")
        if (html.isBlank()) return ToolExecutionResult("Error: html is required", false)
        if (html.length > 50_000) return ToolExecutionResult("Error: html exceeds 50KB", false)
        val title = args.optString("title").ifBlank { "Canvas" }
        val sanitized = CanvasSanitizer.sanitize(html)
        val body = buildString {
            append(MARKER)
            append('\n')
            append("title: ").append(title).append('\n')
            append(sanitized)
        }
        return ToolExecutionResult(body, true, toolTitle = args.optString("tool_title", title))
    }
}

object CanvasSanitizer {
    private val SCRIPT = Regex("(?is)<script\\b[^>]*>.*?</script>")
    private val ONATTR = Regex("(?i)\\son\\w+\\s*=")
    private val JSURL = Regex("(?i)javascript\\s*(:|&#0*58;|&colon;)")
    private val DANGEROUS = Regex("(?is)</?(script|iframe|object|embed|applet|meta|base|form|link|frame|frameset)\\b[^>]*>")

    fun sanitize(html: String): String {
        var s = SCRIPT.replace(html, "")
        s = DANGEROUS.replace(s, "")
        s = ONATTR.replace(s, " data-dropped=")
        s = JSURL.replace(s, "")
        return s
    }

    fun extract(text: String): Pair<String, String>? {
        val idx = text.indexOf(CanvasTool.MARKER)
        if (idx < 0) return null
        val rest = text.substring(idx + CanvasTool.MARKER.length).trimStart()
        val titleLine = rest.lineSequence().firstOrNull().orEmpty()
        val title = titleLine.removePrefix("title:").trim().ifBlank { "Canvas" }
        val html = rest.substringAfter('\n', missingDelimiterValue = rest)
        return title to html
    }
}
