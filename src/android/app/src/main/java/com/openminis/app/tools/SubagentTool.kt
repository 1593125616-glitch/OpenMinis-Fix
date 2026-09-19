package com.openminis.app.tools

import com.openminis.app.data.model.AgentToolDefinition
import com.openminis.app.data.model.AgentToolParam
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * Read-oriented child turn. Depth is capped at 1 so a child cannot spawn
 * another child. The child prompt is prefixed to stay in research mode.
 */
object SubagentTool {
    const val NAME = "subagent"
    private val depth = AtomicInteger(0)

    fun inChild(): Boolean = depth.get() > 0

    fun definition(): AgentToolDefinition = AgentToolDefinition(
        name = NAME,
        description = "Run a short read-only research sub-agent. Use for parallel " +
            "lookup/summarise work. The child cannot write files, run shell, or spawn more sub-agents. " +
            "Pass a self-contained brief; it does not see this chat's tool results.",
        parameters = mapOf(
            "tool_title" to AgentToolParam("string", "5-10 word summary of the sub-task."),
            "prompt" to AgentToolParam("string", "Self-contained research brief for the child."),
        ),
        required = listOf("tool_title", "prompt"),
        propertyOrdering = listOf("tool_title", "prompt"),
    )

    fun enter() { depth.incrementAndGet() }
    fun leave() { depth.decrementAndGet() }

    inline fun <T> runChild(block: () -> T): T {
        enter()
        try {
            return block()
        } finally {
            leave()
        }
    }

    fun parsePrompt(argsJson: String): String {
        val args = JSONObject(argsJson)
        return args.optString("prompt").trim()
    }
}
