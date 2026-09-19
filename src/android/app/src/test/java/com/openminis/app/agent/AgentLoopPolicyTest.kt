package com.openminis.app.agent

import com.openminis.app.data.model.AgentToolDefinition
import com.openminis.app.data.model.AgentToolParam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLoopPolicyTest {
    private fun tool(name: String) = AgentToolDefinition(
        name = name,
        description = name,
        parameters = mapOf("tool_title" to AgentToolParam("string", "t")),
        required = listOf("tool_title"),
    )

    @Test
    fun `plan strips write tools`() {
        val all = listOf("file_read", "shell_execute", "file_write", "browser_use", "subagent").map(::tool)
        val plan = AgentLoopPolicy.filter(all, AgentMode.PLAN).map { it.name }
        assertEquals(listOf("file_read", "browser_use"), plan)
        assertFalse(AgentLoopPolicy.permits(AgentMode.PLAN, "shell_execute"))
        assertTrue(AgentLoopPolicy.permits(AgentMode.AGENT, "shell_execute"))
    }

    @Test
    fun `parse defaults to agent`() {
        assertEquals(AgentMode.PLAN, AgentLoopPolicy.parse("PLAN"))
        assertEquals(AgentMode.AGENT, AgentLoopPolicy.parse("nope"))
        assertEquals(AgentMode.AGENT, AgentLoopPolicy.parse(null))
    }
}
