package com.openminis.app.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelRouterTest {
    private val inbound = ChannelInbound(
        channel = ChannelRouter.CHANNEL_TELEGRAM,
        chatId = "99",
        userId = "42",
        username = "alice",
        text = "hello",
        chatType = "private",
        messageId = "1",
    )

    private fun auth(
        enabled: Boolean = true,
        token: Boolean = true,
        allowed: Set<String> = setOf("42"),
        code: String? = "123456",
    ) = ChannelAuthState(enabled, token, allowed, code)

    @Test
    fun `disabled channel is ignored`() {
        assertEquals(ChannelAction.Ignore, ChannelRouter.decide(inbound, auth(enabled = false)))
    }

    @Test
    fun `missing token is ignored`() {
        assertEquals(ChannelAction.Ignore, ChannelRouter.decide(inbound, auth(token = false)))
    }

    @Test
    fun `group chats are ignored`() {
        val group = inbound.copy(chatType = "group")
        assertEquals(ChannelAction.Ignore, ChannelRouter.decide(group, auth()))
    }

    @Test
    fun `blank text is ignored`() {
        assertEquals(ChannelAction.Ignore, ChannelRouter.decide(inbound.copy(text = "  "), auth()))
    }

    @Test
    fun `unknown user is rejected`() {
        val action = ChannelRouter.decide(inbound, auth(allowed = emptySet()))
        assertTrue(action is ChannelAction.Reject)
    }

    @Test
    fun `pairing with matching code welcomes`() {
        val start = inbound.copy(text = "/start 123456", userId = "77")
        val action = ChannelRouter.decide(start, auth(allowed = emptySet(), code = "123456"))
        assertEquals(ChannelAction.PairAndWelcome("77", ChannelRouter.PAIRED_TEXT), action)
    }

    @Test
    fun `wrong pairing code is rejected`() {
        val start = inbound.copy(text = "/start 000000")
        val action = ChannelRouter.decide(start, auth(allowed = emptySet(), code = "123456"))
        assertTrue(action is ChannelAction.Reject)
    }

    @Test
    fun `allowed user prompt`() {
        assertEquals(ChannelAction.Prompt("hello"), ChannelRouter.decide(inbound, auth()))
    }

    @Test
    fun `allowed user help and new`() {
        assertEquals(
            ChannelAction.Help(ChannelRouter.HELP_TEXT),
            ChannelRouter.decide(inbound.copy(text = "/help"), auth()),
        )
        assertEquals(
            ChannelAction.NewSession,
            ChannelRouter.decide(inbound.copy(text = "/new"), auth()),
        )
        assertEquals(
            ChannelAction.NewSession,
            ChannelRouter.decide(inbound.copy(text = "/reset"), auth()),
        )
    }

    @Test
    fun `unknown slash command is help`() {
        assertEquals(
            ChannelAction.Help(ChannelRouter.HELP_TEXT),
            ChannelRouter.decide(inbound.copy(text = "/nope"), auth()),
        )
    }

    @Test
    fun `parseStartCode only accepts 6 digits`() {
        assertEquals("123456", ChannelRouter.parseStartCode("/start 123456"))
        assertEquals(null, ChannelRouter.parseStartCode("/start abc"))
        assertEquals(null, ChannelRouter.parseStartCode("/start"))
        assertEquals(null, ChannelRouter.parseStartCode("hello"))
    }
}
