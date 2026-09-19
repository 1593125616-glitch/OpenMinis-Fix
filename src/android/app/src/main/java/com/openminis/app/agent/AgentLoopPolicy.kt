package com.openminis.app.agent

import com.openminis.app.data.model.AgentToolDefinition

/**
 * Plan vs Agent, and which tools are mutations. Pure so unit tests don't
 * need ChatViewModel.
 *
 * Plan mode is OpenCode-style: the model may read, search, and look, but
 * cannot shell, write files, spawn sub-agents, or drive the desktop.
 */
enum class AgentMode {
    PLAN,
    AGENT,
}

object AgentLoopPolicy {
    val WRITE_TOOLS = setOf(
        "shell_execute",
        "file_write",
        "file_edit",
        "memory_write",
        "subagent",
        "desktop_run",
    )

    /** Tools that go through Allow / Ask / Deny (OffloadPermissionManager). */
    val GATED_TOOLS = setOf(
        "shell_execute",
        "file_write",
        "file_edit",
        "browser_use",
        "memory_write",
        "desktop_run",
    )

    fun permits(mode: AgentMode, toolName: String): Boolean {
        if (mode == AgentMode.AGENT) return true
        return toolName !in WRITE_TOOLS
    }

    fun filter(tools: List<AgentToolDefinition>, mode: AgentMode): List<AgentToolDefinition> {
        if (mode == AgentMode.AGENT) return tools
        return tools.filter { it.name !in WRITE_TOOLS }
    }

    fun parse(raw: String?): AgentMode =
        if (raw.equals("PLAN", ignoreCase = true)) AgentMode.PLAN else AgentMode.AGENT
}
