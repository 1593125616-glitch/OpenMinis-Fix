package com.openminis.app.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasSanitizerTest {
    @Test
    fun `strips script and handlers`() {
        val dirty = """<div onclick="alert(1)"><script>x()</script>ok</div>"""
        val clean = CanvasSanitizer.sanitize(dirty)
        assertFalse(clean.contains("<script"))
        assertFalse(clean.contains("onclick"))
        assertTrue(clean.contains("ok"))
    }

    @Test
    fun `strips iframe object and javascript url`() {
        val dirty = """<iframe src="https://evil"></iframe><a href="javascript:alert(1)">x</a>"""
        val clean = CanvasSanitizer.sanitize(dirty)
        assertFalse(clean.contains("<iframe"))
        assertFalse(clean.contains("javascript:"))
        assertTrue(clean.contains(">x</a>"))
    }

    @Test
    fun `strips frameset and html-entity javascript url`() {
        val dirty = """<frameset><frame src="x"></frameset><a href="javascript&#58;alert(1)">x</a>"""
        val clean = CanvasSanitizer.sanitize(dirty)
        assertFalse(clean.contains("<frame"))
        assertFalse(clean.contains("javascript"))
        assertTrue(clean.contains(">x</a>"))
    }

    @Test
    fun `execute wraps marker`() {
        val r = CanvasTool.execute("""{"tool_title":"t","html":"<p>hi</p>","title":"Hi"}""")
        assertTrue(r.success)
        assertTrue(r.output.contains(CanvasTool.MARKER))
        val extracted = CanvasSanitizer.extract(r.output)!!
        assertEquals("Hi", extracted.first)
        assertTrue(extracted.second.contains("hi"))
    }
}
