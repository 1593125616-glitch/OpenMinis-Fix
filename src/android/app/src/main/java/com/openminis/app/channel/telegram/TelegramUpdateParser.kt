package com.openminis.app.channel.telegram

import com.openminis.app.channel.ChannelInbound
import com.openminis.app.channel.ChannelRouter
import org.json.JSONObject

data class TelegramUpdate(
    val updateId: Long,
    val inbound: ChannelInbound?,
)

data class TelegramGetUpdatesResult(
    val ok: Boolean,
    val description: String?,
    val updates: List<TelegramUpdate>,
)

object TelegramUpdateParser {
    fun parseGetUpdates(body: String): TelegramGetUpdatesResult {
        val root = JSONObject(body)
        val ok = root.optBoolean("ok", false)
        if (!ok) {
            return TelegramGetUpdatesResult(
                ok = false,
                description = root.optString("description").ifBlank { "telegram_error" },
                updates = emptyList(),
            )
        }
        val arr = root.optJSONArray("result") ?: return TelegramGetUpdatesResult(true, null, emptyList())
        val updates = ArrayList<TelegramUpdate>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            updates.add(parseUpdate(item))
        }
        return TelegramGetUpdatesResult(true, null, updates)
    }

    internal fun parseUpdate(item: JSONObject): TelegramUpdate {
        val updateId = item.optLong("update_id", 0L)
        val message = item.optJSONObject("message") ?: item.optJSONObject("edited_message")
        return TelegramUpdate(updateId, message?.let { parseMessage(it) })
    }

    internal fun parseMessage(message: JSONObject): ChannelInbound? {
        val text = message.optString("text").ifBlank { message.optString("caption") }
        if (text.isBlank()) return null
        val from = message.optJSONObject("from") ?: return null
        val chat = message.optJSONObject("chat") ?: return null
        val userId = from.optLong("id", 0L)
        if (userId == 0L) return null
        val chatId = chat.optLong("id", 0L)
        if (chatId == 0L) return null
        val username = from.optString("username").takeIf { it.isNotBlank() }
        return ChannelInbound(
            channel = ChannelRouter.CHANNEL_TELEGRAM,
            chatId = chatId.toString(),
            userId = userId.toString(),
            username = username,
            text = text,
            chatType = chat.optString("type", "private"),
            messageId = message.optLong("message_id", 0L).toString(),
        )
    }
}
