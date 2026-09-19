package com.openminis.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentStatusNotificationTest {

    @Test
    fun `wall clock when is now minus elapsed`() {
        assertEquals(1_000L, AgentForegroundService.wallClockWhenMs(1_500L, 500L))
        assertEquals(1_500L, AgentForegroundService.wallClockWhenMs(1_500L, -10L))
    }

    @Test
    fun `ticker is current snippet not title plus history`() {
        assertEquals(
            "ls -la",
            AgentForegroundService.tickerLine("Minis is using Shell", "ls -la"),
        )
        assertEquals(
            "Minis is using Shell",
            AgentForegroundService.tickerLine("Minis is using Shell", ""),
        )
        assertEquals("idle", AgentForegroundService.tickerLine("idle", "idle"))
    }

    @Test
    fun `ticker prefers overlay reply excerpt over tool status`() {
        assertEquals(
            "Hello from Minis",
            AgentForegroundService.glanceLine("Idle", "Hello from Minis"),
        )
        assertEquals(
            "Hello from Minis",
            AgentForegroundService.tickerLine("Task completed", "Idle", "Hello from Minis"),
        )
        assertEquals(
            "Task completed",
            AgentForegroundService.tickerLine("Task completed", "Idle", null),
        )
    }
}
