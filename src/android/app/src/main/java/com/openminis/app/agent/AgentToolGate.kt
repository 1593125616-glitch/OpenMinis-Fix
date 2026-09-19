package com.openminis.app.agent

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.openminis.app.offload.OffloadPermissionManager
import com.openminis.app.offload.OffloadPermissionManager.PermissionLevel

/**
 * Combines Plan mode with Allow/Ask/Deny.
 *
 * Headless callers (Telegram, local HTTP, scheduled tasks) must not hang
 * on ASK_ONCE — there is no dialog. Those calls are denied unless the
 * tool is BYPASS. The headless bit is per [sessionId] so a background
 * runner cannot deny the foreground chat (GH#8).
 */
object AgentToolGate {
    suspend fun allow(
        toolName: String,
        toolTitle: String,
        sessionId: String,
        mode: AgentMode,
        interactive: Boolean? = null,
    ): Boolean {
        if (!AgentLoopPolicy.permits(mode, toolName)) return false
        if (toolName !in AgentLoopPolicy.GATED_TOOLS) return true
        // Fail closed: an uninitialized permission store must not silently
        // BYPASS shell/file/desktop.
        val level = runCatching { OffloadPermissionManager.getLevel(toolName) }
            .getOrNull() ?: return false
        val live = interactive ?: isInteractive(sessionId)
        return when (level) {
            PermissionLevel.BYPASS -> true
            PermissionLevel.NOT_ALLOWED -> false
            PermissionLevel.ASK_ONCE -> {
                if (!live) return false
                OffloadPermissionManager.checkPermission(toolName, toolTitle, sessionId)
            }
        }
    }

    fun denyMessage(toolName: String, mode: AgentMode, sessionId: String = ""): String {
        if (!AgentLoopPolicy.permits(mode, toolName)) {
            return "Blocked by Plan mode: $toolName is a write tool. Switch to Agent mode to allow it."
        }
        val level = runCatching { OffloadPermissionManager.getLevel(toolName) }.getOrNull()
        val rule = denyRule(
            level = level,
            headless = HeadlessGuard.inHeadless(sessionId),
            appForeground = isAppForeground(),
        )
        val levelName = level?.name ?: "uninitialized"
        return "Blocked by tool permission: $toolName level=$levelName rule=$rule"
    }

    fun denyRule(
        level: PermissionLevel?,
        headless: Boolean,
        appForeground: Boolean,
    ): String = when {
        level == null -> "permission-store-uninitialized"
        level == PermissionLevel.NOT_ALLOWED -> "deny"
        level == PermissionLevel.ASK_ONCE && headless -> "headless-ask-denied"
        level == PermissionLevel.ASK_ONCE && !appForeground -> "app-background-ask-denied"
        level == PermissionLevel.ASK_ONCE -> "ask-not-confirmed"
        else -> "deny"
    }

    fun isInteractive(sessionId: String): Boolean {
        if (HeadlessGuard.inHeadless(sessionId)) return false
        return isAppForeground()
    }

    fun isInteractive(): Boolean = isAppForeground()

    fun isAppForeground(): Boolean = runCatching {
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }.getOrDefault(false)

    @Suppress("UNUSED_PARAMETER")
    fun isInteractive(context: Context): Boolean = isAppForeground()
}
