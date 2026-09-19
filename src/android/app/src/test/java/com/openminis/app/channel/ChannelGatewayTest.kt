package com.openminis.app.channel

import com.openminis.app.channel.telegram.TelegramBotClient
import com.openminis.app.channel.telegram.TelegramHttp
import com.openminis.app.debug.HeadlessChatRunner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelGatewayTest {
    @Test
    fun `reject does not call the agent`() = runBlocking {
        val sent = mutableListOf<Pair<String, String>>()
        val prompts = mutableListOf<String>()
        val store = FakeStore(allowed = emptySet())
        gateway(sent, prompts, store).handle(inbound("pwn the phone", userId = "1"))
        assertTrue(prompts.isEmpty())
        assertEquals(1, sent.size)
        assertTrue(sent[0].second.contains("Not authorized"))
    }

    @Test
    fun `pairing adds the user and rotates the code`() = runBlocking {
        val sent = mutableListOf<Pair<String, String>>()
        val store = FakeStore(allowed = emptySet(), pairing = "123456")
        gateway(sent, mutableListOf(), store).handle(inbound("/start 123456", userId = "77"))
        assertTrue(store.allowed.contains("77"))
        assertTrue(store.pairing != "123456")
        assertTrue(sent.last().second.contains("Paired"))
    }

    @Test
    fun `allowed prompt is forwarded and reply sent`() = runBlocking {
        val sent = mutableListOf<Pair<String, String>>()
        val prompts = mutableListOf<String>()
        val store = FakeStore(allowed = setOf("1"))
        gateway(sent, prompts, store).handle(inbound("what time is it", userId = "1"))
        assertEquals(listOf("what time is it"), prompts)
        assertEquals("agent says hi", sent.last().second)
        assertEquals("sess-1", store.sessions["9"])
    }

    @Test
    fun `new command unbinds the session`() = runBlocking {
        val sent = mutableListOf<Pair<String, String>>()
        val store = FakeStore(allowed = setOf("1")).also { it.sessions["9"] = "old" }
        gateway(sent, mutableListOf(), store).handle(inbound("/new", userId = "1"))
        assertTrue(store.sessions.isEmpty())
        assertEquals(ChannelRouter.NEW_SESSION_TEXT, sent.last().second)
    }

    private fun inbound(text: String, userId: String) = ChannelInbound(
        channel = ChannelRouter.CHANNEL_TELEGRAM,
        chatId = "9",
        userId = userId,
        username = null,
        text = text,
        chatType = "private",
        messageId = "1",
    )

    private fun gateway(
        sent: MutableList<Pair<String, String>>,
        prompts: MutableList<String>,
        store: FakeStore,
    ): ChannelGateway {
        val http = TelegramHttp { _, method, params ->
            if (method == "sendMessage") {
                sent += (params.getValue("chat_id") to params.getValue("text"))
            }
            """{"ok":true,"result":true}"""
        }
        val telegram = TelegramBotClient(
            tokenProvider = { "12345:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" },
            http = http,
        )
        return ChannelGateway(
            prefs = store,
            telegram = telegram,
            runPrompt = { _, text ->
                prompts += text
                HeadlessChatRunner.PromptResult(
                    status = "Completed",
                    responseText = "agent says hi",
                    timedOut = false,
                )
            },
            ensureSession = { "sess-1" },
            sessionExists = { id -> store.sessions.containsValue(id) },
            isReady = { true },
            startForeground = {},
        )
    }

    private class FakeStore(
        allowed: Set<String>,
        var pairing: String = "123456",
    ) : ChannelStore {
        val allowed = allowed.toMutableSet()
        val sessions = mutableMapOf<String, String>()
        override fun authState() = ChannelAuthState(true, true, allowed.toSet(), pairing)
        override fun addAllowedUserId(userId: String) { allowed += userId }
        override fun rotatePairingCode(): String {
            pairing = "654321"
            return pairing
        }
        override fun sessionIdFor(chatId: String) = sessions[chatId]
        override fun bindSession(chatId: String, sessionId: String) { sessions[chatId] = sessionId }
        override fun unbindSession(chatId: String) { sessions.remove(chatId) }
    }
}
