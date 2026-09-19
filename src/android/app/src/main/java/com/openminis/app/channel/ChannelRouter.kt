package com.openminis.app.channel

import java.security.MessageDigest

/**
 * Pure decision table for inbound channel messages.
 *
 * Deny-by-default: unknown users cannot prompt the agent. Pairing is the
 * only way a new Telegram user gets onto the allowlist (or the operator
 * pastes their user id in Settings).
 */
object ChannelRouter {
    const val CHANNEL_TELEGRAM = "telegram"

    const val HELP_TEXT =
        "Minis channel commands:\n" +
            "/help — this message\n" +
            "/new or /reset — start a fresh chat\n" +
            "/start — status\n\n" +
            "Anything else is sent to the agent."

    const val PAIRED_TEXT =
        "Paired. This Telegram account can talk to Minis.\n\n$HELP_TEXT"

    const val UNAUTHORIZED_TEXT =
        "Not authorized. Open Minis → Settings → Channels, then send:\n" +
            "/start <pairing-code>"

    const val NEW_SESSION_TEXT = "Started a new chat."

    fun decide(inbound: ChannelInbound, auth: ChannelAuthState): ChannelAction {
        if (!auth.enabled || !auth.botTokenPresent) return ChannelAction.Ignore
        if (inbound.channel != CHANNEL_TELEGRAM) return ChannelAction.Ignore
        // Groups would let any member talk to the bot. Private only for v1.
        if (inbound.chatType != "private") return ChannelAction.Ignore
        val text = inbound.text.trim()
        if (text.isEmpty()) return ChannelAction.Ignore

        val allowed = inbound.userId in auth.allowedUserIds
        if (!allowed) {
            val code = parseStartCode(text)
            val expected = auth.pairingCode
            if (code != null && expected != null && pairingMatches(code, expected)) {
                return ChannelAction.PairAndWelcome(inbound.userId, PAIRED_TEXT)
            }
            return ChannelAction.Reject(UNAUTHORIZED_TEXT)
        }

        val cmd = text.lowercase().substringBefore(' ')
        return when (cmd) {
            "/help" -> ChannelAction.Help(HELP_TEXT)
            "/start" -> {
                val code = parseStartCode(text)
                if (code != null && auth.pairingCode != null && pairingMatches(code, auth.pairingCode)) {
                    ChannelAction.PairAndWelcome(inbound.userId, PAIRED_TEXT)
                } else {
                    ChannelAction.Help(HELP_TEXT)
                }
            }
            "/new", "/reset" -> ChannelAction.NewSession
            else -> {
                if (text.startsWith("/")) ChannelAction.Help(HELP_TEXT)
                else ChannelAction.Prompt(text)
            }
        }
    }

    internal fun parseStartCode(text: String): String? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("/start")) return null
        val rest = trimmed.removePrefix("/start").trim()
        if (rest.isEmpty()) return null
        // BotFather deep-links replace spaces with the payload only.
        return rest.substringBefore(' ').takeIf { it.matches(Regex("""\d{6}""")) }
    }

    internal fun pairingMatches(given: String, expected: String): Boolean {
        val a = given.toByteArray(Charsets.UTF_8)
        val b = expected.toByteArray(Charsets.UTF_8)
        if (a.size != b.size) return false
        return MessageDigest.isEqual(a, b)
    }
}
