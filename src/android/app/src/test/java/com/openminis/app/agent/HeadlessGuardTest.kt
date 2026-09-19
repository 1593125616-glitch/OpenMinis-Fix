package com.openminis.app.agent

import com.openminis.app.offload.OffloadPermissionManager.PermissionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HeadlessGuardTest {
    @Before
    fun reset() {
        HeadlessGuard.resetForTest()
    }

    @Test
    fun `headless is scoped to the marked session`() {
        assertFalse(HeadlessGuard.inHeadless("chat"))
        HeadlessGuard.enter("sched")
        try {
            assertTrue(HeadlessGuard.inHeadless("sched"))
            assertFalse(HeadlessGuard.inHeadless("chat"))
            assertFalse(AgentToolGate.isInteractive("sched"))
        } finally {
            HeadlessGuard.leave("sched")
        }
        assertFalse(HeadlessGuard.inHeadless("sched"))
        assertFalse(HeadlessGuard.inHeadless("chat"))
    }

    @Test
    fun `nested enter on one session does not leak to another`() {
        HeadlessGuard.enter("sched")
        HeadlessGuard.enter("sched")
        HeadlessGuard.leave("sched")
        assertTrue(HeadlessGuard.inHeadless("sched"))
        assertFalse(HeadlessGuard.inHeadless("chat"))
        HeadlessGuard.leave("sched")
        assertFalse(HeadlessGuard.inHeadless("sched"))
    }

    @Test
    fun `extra leave does not go negative`() {
        HeadlessGuard.leave("s")
        assertFalse(HeadlessGuard.inHeadless("s"))
        HeadlessGuard.enter("s")
        HeadlessGuard.leave("s")
        assertFalse(HeadlessGuard.inHeadless("s"))
    }

    @Test
    fun `deny rule names the headless ask path`() {
        assertEquals(
            "headless-ask-denied",
            AgentToolGate.denyRule(PermissionLevel.ASK_ONCE, headless = true, appForeground = true),
        )
        assertEquals(
            "app-background-ask-denied",
            AgentToolGate.denyRule(PermissionLevel.ASK_ONCE, headless = false, appForeground = false),
        )
        assertEquals(
            "deny",
            AgentToolGate.denyRule(PermissionLevel.NOT_ALLOWED, headless = false, appForeground = true),
        )
        assertEquals(
            "ask-not-confirmed",
            AgentToolGate.denyRule(PermissionLevel.ASK_ONCE, headless = false, appForeground = true),
        )
    }
}
