package com.openminis.app.channel

import com.openminis.app.channel.telegram.TelegramUpdateParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramUpdateParserTest {
    @Test
    fun `parses private text message`() {
        val body = """
            {"ok":true,"result":[{
              "update_id": 9,
              "message": {
                "message_id": 3,
                "from": {"id": 42, "username": "alice"},
                "chat": {"id": 42, "type": "private"},
                "text": "hello minis"
              }
            }]}
        """.trimIndent()
        val parsed = TelegramUpdateParser.parseGetUpdates(body)
        assertTrue(parsed.ok)
        assertEquals(1, parsed.updates.size)
        assertEquals(9L, parsed.updates[0].updateId)
        val inbound = parsed.updates[0].inbound!!
        assertEquals("42", inbound.userId)
        assertEquals("42", inbound.chatId)
        assertEquals("private", inbound.chatType)
        assertEquals("hello minis", inbound.text)
        assertEquals("alice", inbound.username)
    }

    @Test
    fun `sticker without text is skipped`() {
        val body = """
            {"ok":true,"result":[{
              "update_id": 1,
              "message": {
                "message_id": 1,
                "from": {"id": 1},
                "chat": {"id": 1, "type": "private"},
                "sticker": {"file_id": "x"}
              }
            }]}
        """.trimIndent()
        val parsed = TelegramUpdateParser.parseGetUpdates(body)
        assertTrue(parsed.ok)
        assertNull(parsed.updates[0].inbound)
    }

    @Test
    fun `ok false surfaces description without throwing`() {
        val parsed = TelegramUpdateParser.parseGetUpdates(
            """{"ok":false,"description":"Unauthorized"}""",
        )
        assertFalse(parsed.ok)
        assertEquals("Unauthorized", parsed.description)
        assertTrue(parsed.updates.isEmpty())
    }
}
