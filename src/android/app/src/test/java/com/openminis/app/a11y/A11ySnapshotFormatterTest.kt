package com.openminis.app.a11y

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class A11ySnapshotFormatterTest {
    @Test
    fun `formats refs`() {
        val line = A11ySnapshotFormatter.line(
            3,
            A11yNode("a3f2", "button", "Submit", clickable = true, editable = false),
        )
        assertTrue(line.startsWith("@e3"))
        assertTrue(line.contains("id=a3f2"))
        assertTrue(line.contains("Submit"))
        assertEquals(3, A11ySnapshotFormatter.parseRef("@e3"))
        assertEquals(null, A11ySnapshotFormatter.parseRef("a3f2"))
    }
}
