package com.openminis.app.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadlessGuardTest {
    @Test
    fun `interactive is false while headless`() {
        assertFalse(HeadlessGuard.inHeadless())
        HeadlessGuard.enter()
        try {
            assertTrue(HeadlessGuard.inHeadless())
            assertFalse(AgentToolGate.isInteractive())
        } finally {
            HeadlessGuard.leave()
        }
        assertFalse(HeadlessGuard.inHeadless())
    }

    @Test
    fun `extra leave does not go negative`() {
        HeadlessGuard.leave()
        assertFalse(HeadlessGuard.inHeadless())
        HeadlessGuard.enter()
        HeadlessGuard.leave()
        assertFalse(HeadlessGuard.inHeadless())
    }
}
