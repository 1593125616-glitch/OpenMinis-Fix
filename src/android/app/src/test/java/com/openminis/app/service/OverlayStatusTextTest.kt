package com.openminis.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayStatusTextTest {
    @Test
    fun `excerpt is the tail not the first sentence`() {
        val text = "First sentence of the whole reply. " + "later ".repeat(40) + "CURRENT"
        val excerpt = OverlayStatusText.excerptTail(text)!!
        assertTrue(excerpt.startsWith("…"))
        assertTrue(excerpt.endsWith("CURRENT"))
        assertTrue(!excerpt.contains("First sentence"))
        assertTrue(excerpt.length <= OverlayStatusText.MAX_CHARS)
    }

    @Test
    fun `running glance prefers current tool title`() {
        assertEquals(
            "Open Baidu home page",
            OverlayStatusText.glance(
                isRunning = true,
                toolTitle = "Open Baidu home page",
                toolName = "browser_use",
                status = "Running: browser_use",
                replyExcerpt = "First sentence of an old reply",
                fallback = "Minis is running",
            ),
        )
    }

    @Test
    fun `completed glance is the reply snippet`() {
        assertEquals(
            "just said this",
            OverlayStatusText.glance(
                isRunning = false,
                toolTitle = "Shell",
                toolName = "shell_execute",
                status = "Idle",
                replyExcerpt = "just said this",
                fallback = "Task completed",
            ),
        )
    }

    @Test
    fun `marquee lyric loops a short line so status bar can scroll`() {
        val lyric = OverlayStatusText.marqueeLyric("Open Baidu")
        assertTrue(lyric.startsWith("Open Baidu"))
        assertTrue(lyric.length > "Open Baidu".length)
        assertTrue(lyric.contains("Open Baidu          Open Baidu"))
        assertTrue(!OverlayStatusText.shouldReplaceLyric(lyric, lyric))
        assertTrue(OverlayStatusText.shouldReplaceLyric(lyric, OverlayStatusText.marqueeLyric("tap Login")))
    }
}
