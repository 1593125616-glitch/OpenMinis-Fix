package com.openminis.app.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelPrefsLogicTest {
    @Test
    fun `bot token regex`() {
        assertTrue(ChannelPrefs.isValidBotToken("123456:AAHxxxxxxxxxxxxxxxxxxxxxxxxxxx"))
        assertFalse(ChannelPrefs.isValidBotToken(""))
        assertFalse(ChannelPrefs.isValidBotToken("not-a-token"))
        assertFalse(ChannelPrefs.isValidBotToken("123456:short"))
        assertFalse(ChannelPrefs.isValidBotToken("https://api.telegram.org/bot123:AAH"))
    }

    @Test
    fun `user id parsing`() {
        assertEquals(
            setOf("1", "2", "-100123"),
            ChannelPrefs.parseUserIds("1, 2; -100123 extra"),
        )
        assertEquals("", ChannelPrefs.normalizeUserIds("nope"))
    }
}
