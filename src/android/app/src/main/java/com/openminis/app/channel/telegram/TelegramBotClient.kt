package com.openminis.app.channel.telegram

import com.openminis.app.channel.ChannelPrefs
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

fun interface TelegramHttp {
    fun call(token: String, method: String, params: Map<String, String>): String
}

class OkHttpTelegramHttp(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(70, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
) : TelegramHttp {
    override fun call(token: String, method: String, params: Map<String, String>): String {
        if (!ChannelPrefs.isValidBotToken(token)) {
            throw IOException("invalid_bot_token")
        }
        // Token contains ':' — keep it in the path unparsed via a pre-validated
        // literal so we never interpolate untrusted method names into the host.
        if (!METHOD_REGEX.matches(method)) throw IOException("invalid_method")
        val url = "https://api.telegram.org/bot$token/$method"
        val form = FormBody.Builder()
        params.forEach { (k, v) -> form.add(k, v) }
        val req = Request.Builder().url(url).post(form.build()).build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("telegram HTTP ${resp.code}")
            }
            return body
        }
    }

    companion object {
        private val METHOD_REGEX = Regex("""^[a-zA-Z][a-zA-Z0-9]+$""")
    }
}

class TelegramBotClient(
    private val tokenProvider: () -> String,
    private val http: TelegramHttp = OkHttpTelegramHttp(),
) {
    fun getUpdates(offset: Long, timeoutSec: Int): String {
        return http.call(
            tokenProvider(),
            "getUpdates",
            mapOf(
                "offset" to offset.toString(),
                "timeout" to timeoutSec.toString(),
                "allowed_updates" to """["message"]""",
            ),
        )
    }

    fun sendMessage(chatId: String, text: String) {
        http.call(
            tokenProvider(),
            "sendMessage",
            mapOf(
                "chat_id" to chatId,
                "text" to text,
                "disable_web_page_preview" to "true",
            ),
        )
    }

    fun sendChatAction(chatId: String, action: String = "typing") {
        runCatching {
            http.call(
                tokenProvider(),
                "sendChatAction",
                mapOf("chat_id" to chatId, "action" to action),
            )
        }
    }
}
