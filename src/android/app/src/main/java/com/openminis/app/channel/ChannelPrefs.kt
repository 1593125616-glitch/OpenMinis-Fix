package com.openminis.app.channel

import android.content.Context
import android.content.SharedPreferences
import com.openminis.app.util.EncryptedPrefsFactory
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Encrypted store for the Telegram bot token, allowlist, pairing code, and
 * chat→session map. Non-secret flags (enabled, offset, last error) live in
 * the same file so a Keystore wipe drops the whole channel config together.
 */
class ChannelPrefs(context: Context) : ChannelStore {
    private val prefs: SharedPreferences =
        EncryptedPrefsFactory.safeCreate(context.applicationContext, PREFS_FILE)

    fun isTelegramEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setTelegramEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun botToken(): String = prefs.getString(KEY_TOKEN, "") ?: ""

    fun setBotToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token.trim()).apply()
    }

    fun hasBotToken(): Boolean = botToken().isNotBlank()

    fun allowedUserIds(): Set<String> =
        parseUserIds(prefs.getString(KEY_ALLOWED, "") ?: "")

    fun setAllowedUserIdsRaw(raw: String) {
        prefs.edit().putString(KEY_ALLOWED, normalizeUserIds(raw)).apply()
    }

    fun allowedUserIdsRaw(): String = prefs.getString(KEY_ALLOWED, "") ?: ""

    override fun addAllowedUserId(userId: String) {
        val next = allowedUserIds() + userId.trim()
        prefs.edit().putString(KEY_ALLOWED, next.filter { it.isNotBlank() }.joinToString(",")).apply()
    }

    fun pairingCode(): String {
        val existing = prefs.getString(KEY_PAIRING, "") ?: ""
        if (existing.matches(PAIRING_REGEX)) return existing
        val generated = generatePairingCode()
        prefs.edit().putString(KEY_PAIRING, generated).apply()
        return generated
    }

    override fun rotatePairingCode(): String {
        val generated = generatePairingCode()
        prefs.edit().putString(KEY_PAIRING, generated).apply()
        return generated
    }

    fun updateOffset(): Long = prefs.getLong(KEY_OFFSET, 0L)

    fun setUpdateOffset(offset: Long) {
        prefs.edit().putLong(KEY_OFFSET, offset).apply()
    }

    override fun sessionIdFor(chatId: String): String? {
        val map = sessionMap()
        return map.optString(chatId).takeIf { it.isNotBlank() }
    }

    override fun bindSession(chatId: String, sessionId: String) {
        val map = sessionMap()
        map.put(chatId, sessionId)
        prefs.edit().putString(KEY_SESSIONS, map.toString()).apply()
    }

    override fun unbindSession(chatId: String) {
        val map = sessionMap()
        map.remove(chatId)
        prefs.edit().putString(KEY_SESSIONS, map.toString()).apply()
    }

    fun lastError(): String = prefs.getString(KEY_ERROR, "") ?: ""

    fun setLastError(error: String?) {
        prefs.edit().putString(KEY_ERROR, error?.take(300) ?: "").apply()
    }

    fun lastOkAt(): Long = prefs.getLong(KEY_LAST_OK, 0L)

    fun markOk() {
        prefs.edit().putLong(KEY_LAST_OK, System.currentTimeMillis()).putString(KEY_ERROR, "").apply()
    }

    fun isLocalHttpEnabled(): Boolean = prefs.getBoolean(KEY_HTTP, false)
    fun setLocalHttpEnabled(v: Boolean) { prefs.edit().putBoolean(KEY_HTTP, v).apply() }
    fun isLanBind(): Boolean = prefs.getBoolean(KEY_LAN, false)
    fun setLanBind(v: Boolean) { prefs.edit().putBoolean(KEY_LAN, v).apply() }
    fun desktopBaseUrl(): String = prefs.getString(KEY_DESKTOP_URL, "") ?: ""
    fun setDesktopBaseUrl(v: String) { prefs.edit().putString(KEY_DESKTOP_URL, v.trim()).apply() }
    fun desktopToken(): String = prefs.getString(KEY_DESKTOP_TOKEN, "") ?: ""
    fun setDesktopToken(v: String) { prefs.edit().putString(KEY_DESKTOP_TOKEN, v.trim()).apply() }

    override fun authState(): ChannelAuthState = ChannelAuthState(
        enabled = isTelegramEnabled(),
        botTokenPresent = hasBotToken(),
        allowedUserIds = allowedUserIds(),
        pairingCode = pairingCode(),
    )

    private fun sessionMap(): JSONObject {
        val raw = prefs.getString(KEY_SESSIONS, "") ?: ""
        return try {
            if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    companion object {
        private const val PREFS_FILE = "minis_channel_prefs"
        private const val KEY_ENABLED = "telegram.enabled"
        private const val KEY_TOKEN = "telegram.bot_token"
        private const val KEY_ALLOWED = "telegram.allowed_user_ids"
        private const val KEY_PAIRING = "telegram.pairing_code"
        private const val KEY_OFFSET = "telegram.update_offset"
        private const val KEY_SESSIONS = "telegram.session_map"
        private const val KEY_ERROR = "telegram.last_error"
        private const val KEY_LAST_OK = "telegram.last_ok_at"
        private const val KEY_HTTP = "local.http.enabled"
        private const val KEY_LAN = "local.http.lan"
        private const val KEY_DESKTOP_URL = "desktop.base_url"
        private const val KEY_DESKTOP_TOKEN = "desktop.token"

        val BOT_TOKEN_REGEX = Regex("""^\d{5,}:[A-Za-z0-9_-]{20,}$""")
        private val PAIRING_REGEX = Regex("""^\d{6}$""")
        private val USER_ID_REGEX = Regex("""^-?\d+$""")

        fun isValidBotToken(token: String): Boolean = BOT_TOKEN_REGEX.matches(token.trim())

        fun parseUserIds(raw: String): Set<String> =
            raw.split(',', ';', ' ', '\n', '\t')
                .map { it.trim() }
                .filter { it.matches(USER_ID_REGEX) }
                .toSet()

        fun normalizeUserIds(raw: String): String = parseUserIds(raw).joinToString(",")

        fun generatePairingCode(): String {
            val n = SecureRandom().nextInt(1_000_000)
            return String.format("%06d", n)
        }
    }
}
