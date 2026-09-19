package com.openminis.app.channel

/**
 * One inbound chat message from any IM channel. Keep this Android-free so
 * [ChannelRouter] can be unit-tested without Robolectric.
 */
data class ChannelInbound(
    val channel: String,
    val chatId: String,
    val userId: String,
    val username: String?,
    val text: String,
    val chatType: String,
    val messageId: String,
)

data class ChannelAuthState(
    val enabled: Boolean,
    val botTokenPresent: Boolean,
    val allowedUserIds: Set<String>,
    val pairingCode: String?,
)

sealed class ChannelAction {
    data object Ignore : ChannelAction()
    data class Reject(val reply: String) : ChannelAction()
    data class PairAndWelcome(val userId: String, val reply: String) : ChannelAction()
    data class Help(val reply: String) : ChannelAction()
    data object NewSession : ChannelAction()
    data class Prompt(val text: String) : ChannelAction()
}

/** Persistence ChannelGateway needs. [ChannelPrefs] is the production impl. */
interface ChannelStore {
    fun authState(): ChannelAuthState
    fun addAllowedUserId(userId: String)
    fun rotatePairingCode(): String
    fun sessionIdFor(chatId: String): String?
    fun bindSession(chatId: String, sessionId: String)
    fun unbindSession(chatId: String)
}
