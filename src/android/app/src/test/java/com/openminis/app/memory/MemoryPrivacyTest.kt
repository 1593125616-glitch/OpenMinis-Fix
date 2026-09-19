package com.openminis.app.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MemoryPrivacyTest {
    @Test
    fun `strips private blocks`() {
        val src = "visible\n<private>secret</private>\nstill"
        assertEquals("visible\n\nstill", MemoryPrivacy.stripPrivate(src))
    }

    @Test
    fun `unclosed private drops the rest`() {
        val src = "visible\n<private>secret\nand more"
        assertEquals("visible\n", MemoryPrivacy.stripPrivate(src))
    }

    @Test
    fun `cite format`() {
        assertEquals("[2026-09-18.md:L4] hello", MemoryPrivacy.cite("2026-09-18.md", 4, "  hello"))
        assertFalse(MemoryPrivacy.isPrivateLine("normal"))
    }
}
