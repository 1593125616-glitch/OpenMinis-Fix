package com.openminis.app.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyPoolTest {
    @Test
    fun `parse splits keys`() {
        assertEquals(
            listOf("sk-12345678", "sk-abcdefgh"),
            KeyPool.parse("sk-12345678\nsk-abcdefgh,too"),
        )
    }

    @Test
    fun `pick sticks until the last key fails`() {
        val id = "stick-${System.nanoTime()}"
        val a = KeyPool.pick(id, "primary-key-aaa", listOf("extra-key-bbb"))
        val b = KeyPool.pick(id, "primary-key-aaa", listOf("extra-key-bbb"))
        assertEquals(a, b)
        KeyPool.markLastFailed(id)
        val c = KeyPool.pick(id, "primary-key-aaa", listOf("extra-key-bbb"))
        assertTrue(c in listOf("primary-key-aaa", "extra-key-bbb"))
        assertTrue(c != a)
    }

    @Test
    fun `failed key is skipped`() {
        val id = "skip-${System.nanoTime()}"
        KeyPool.markFailed(id, "dead-key-zzzzzzzz")
        val picked = KeyPool.pick(id, "dead-key-zzzzzzzz", listOf("live-key-yyyyyyyy"))
        assertEquals("live-key-yyyyyyyy", picked)
    }
}
