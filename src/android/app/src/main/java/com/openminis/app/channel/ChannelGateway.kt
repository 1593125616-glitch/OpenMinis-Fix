package com.openminis.app.channel

import android.content.Context
import android.util.Log
import com.openminis.app.MinisApp
import com.openminis.app.channel.telegram.TelegramBotClient
import com.openminis.app.debug.HeadlessChatRunner
import com.openminis.app.logging.AppLogger
import com.openminis.app.service.AgentForegroundService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns a routed [ChannelAction] into Telegram replies and (for prompts)
 * a headless [ChatViewModel] turn via [HeadlessChatRunner].
 */
internal class ChannelGateway(
    private val prefs: ChannelStore,
    private val telegram: TelegramBotClient,
    private val runPrompt: suspend (sessionId: String, text: String) -> HeadlessChatRunner.PromptResult,
    private val ensureSession: suspend () -> String,
    private val sessionExists: suspend (String) -> Boolean,
    private val isReady: () -> Boolean,
    private val startForeground: () -> Unit = {},
) {
    private val chatLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun handle(inbound: ChannelInbound) {
        val lock = chatLocks.getOrPut(inbound.chatId) { Mutex() }
        lock.withLock { handleLocked(inbound) }
    }

    private suspend fun handleLocked(inbound: ChannelInbound) {
        val action = ChannelRouter.decide(inbound, prefs.authState())
        when (action) {
            ChannelAction.Ignore -> Unit
            is ChannelAction.Reject -> reply(inbound.chatId, action.reply)
            is ChannelAction.Help -> reply(inbound.chatId, action.reply)
            is ChannelAction.PairAndWelcome -> {
                prefs.addAllowedUserId(action.userId)
                prefs.rotatePairingCode()
                reply(inbound.chatId, action.reply)
            }
            ChannelAction.NewSession -> {
                prefs.unbindSession(inbound.chatId)
                reply(inbound.chatId, ChannelRouter.NEW_SESSION_TEXT)
            }
            is ChannelAction.Prompt -> handlePrompt(inbound, action.text)
        }
    }

    private suspend fun handlePrompt(inbound: ChannelInbound, text: String) {
        if (!isReady()) {
            reply(inbound.chatId, "Minis is still starting. Try again in a moment.")
            return
        }
        telegram.sendChatAction(inbound.chatId)
        val sessionId = resolveSession(inbound.chatId)
        startForeground()
        val result = try {
            runPrompt(sessionId, text)
        } catch (t: Throwable) {
            Log.w(TAG, "prompt failed: ${t.javaClass.simpleName}")
            AppLogger.info(TAG, "channel prompt failed: ${t.javaClass.simpleName}")
            reply(inbound.chatId, "Agent error. Open the app and check the Telegram session.")
            return
        }
        val body = when {
            result.status == "Error" -> result.responseText?.takeIf { it.isNotBlank() }
                ?: "Agent error (${result.status})."
            result.timedOut -> result.responseText?.trim()?.takeIf { it.isNotEmpty() }
                ?: "Still working — open the Minis app to watch the session."
            else -> result.responseText?.trim().orEmpty().ifBlank {
                "Done. Open the Minis app if you need the full transcript."
            }
        }
        reply(inbound.chatId, body)
    }

    private suspend fun resolveSession(chatId: String): String {
        val existing = prefs.sessionIdFor(chatId)
        if (existing != null && sessionExists(existing)) return existing
        val id = ensureSession()
        prefs.bindSession(chatId, id)
        return id
    }

    private fun reply(chatId: String, text: String) {
        val chunks = TelegramMessageSplitter.split(text)
        for (chunk in chunks) {
            runCatching { telegram.sendMessage(chatId, chunk) }
                .onFailure { t ->
                    Log.w(TAG, "sendMessage failed: ${t.javaClass.simpleName}")
                }
        }
    }

    companion object {
        private const val TAG = "ChannelGateway"
        const val PROMPT_TIMEOUT_MS = 10 * 60 * 1000L

        fun live(context: Context, prefs: ChannelPrefs, telegram: TelegramBotClient): ChannelGateway {
            val appContext = context.applicationContext
            return ChannelGateway(
                prefs = prefs,
                telegram = telegram,
                runPrompt = { sessionId, text ->
                    HeadlessChatRunner.prompt(
                        context = appContext,
                        sessionId = sessionId,
                        text = text,
                        wait = true,
                        timeoutMs = PROMPT_TIMEOUT_MS,
                    )
                },
                ensureSession = {
                    val id = HeadlessChatRunner.ensureSession(appContext)
                    HeadlessChatRunner.applyModelOverride(appContext, id, null, null)
                    val app = appContext as MinisApp
                    runCatching { app.chatRepository.dao.updateSource(id, "telegram") }
                    id
                },
                sessionExists = { id ->
                    val app = appContext as? MinisApp
                    app != null && app.subsystemsReady() && app.chatRepository.getSession(id) != null
                },
                isReady = {
                    val app = appContext as? MinisApp
                    app != null && app.subsystemsReady()
                },
                startForeground = {
                    runCatching { AgentForegroundService.startService(appContext, 1, "Telegram") }
                },
            )
        }
    }
}
