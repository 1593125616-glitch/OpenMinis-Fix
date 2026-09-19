package com.openminis.app.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramMessageSplitterTest {
    @Test
    fun `short text is one chunk`() {
        assertEquals(listOf("hi"), TelegramMessageSplitter.split("hi", 10))
    }

    @Test
    fun `splits on newline when possible`() {
        val chunks = TelegramMessageSplitter.split("aaaa\nbbbb\ncccc", 8)
        assertEquals(listOf("aaaa", "bbbb", "cccc"), chunks)
    }

    @Test
    fun `hard-splits when no newline`() {
        val chunks = TelegramMessageSplitter.split("abcdefghij", 4)
        assertEquals(listOf("abcd", "efgh", "ij"), chunks)
    }

    @Test
    fun `empty is empty`() {
        assertTrue(TelegramMessageSplitter.split("").isEmpty())
    }
}
