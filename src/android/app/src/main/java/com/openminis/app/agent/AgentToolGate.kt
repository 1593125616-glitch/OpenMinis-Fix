package com.openminis.app.agent

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.openminis.app.offload.OffloadPermissionManager

/**
 * Combines Plan mode with Allow/Ask/Deny.
 *
 * Headless callers (Telegram, local HTTP) must not hang on ASK_ONCE — there
 * is no dialog. Those calls are denied unless the tool is BYPASS.
 */
object AgentToolGate {
    suspend fun allow(
        toolName: String,
        toolTitle: String,
        sessionId: String,
        mode: AgentMode,
        interactive: Boolean = isInteractive(),
    ): Boolean {
        if (!AgentLoopPolicy.permits(mode, toolName)) return false
        if (toolName !in AgentLoopPolicy.GATED_TOOLS) return true
        // Fail closed: an uninitialized permission store must not silently
        // BYPASS shell/file/desktop.
        val level = runCatching { OffloadPermissionManager.getLevel(toolName) }
            .getOrNull() ?: return false
        return when (level) {
            OffloadPermissionManager.PermissionLevel.BYPASS -> true
            OffloadPermissionManager.PermissionLevel.NOT_ALLOWED -> false
            OffloadPermissionManager.PermissionLevel.ASK_ONCE -> {
                if (!interactive) return false
                OffloadPermissionManager.checkPermission(toolName, toolTitle, sessionId)
            }
        }
    }

    fun denyMessage(toolName: String, mode: AgentMode): String {
        return if (!AgentLoopPolicy.permits(mode, toolName)) {
            "Blocked by Plan mode: $toolName is a write tool. Switch to Agent mode to allow it."
        } else {
            "Blocked by tool permission: $toolName is Deny, or Ask was not confirmed (headless Ask is denied)."
        }
    }

    fun isInteractive(): Boolean {
        if (HeadlessGuard.inHeadless()) return false
        return runCatching {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }.getOrDefault(false)
    }

    @Suppress("UNUSED_PARAMETER")
    fun isInteractive(context: Context): Boolean = isInteractive()
}
