package com.openminis.app.agent

import java.util.concurrent.ConcurrentHashMap

/**
 * Telegram / local HTTP / scheduled tasks / subagent run through
 * HeadlessChatRunner. ASK_ONCE must not wait on a dialog those callers
 * cannot see.
 *
 * The mark is **per session**. A scheduled task must not flip the
 * foreground chat into headless — that was GH#8 (intermittent
 * "headless Ask is denied" while the user was in the chat UI).
 */
object HeadlessGuard {
    private val depths = ConcurrentHashMap<String, Int>()

    fun inHeadless(sessionId: String): Boolean =
        sessionId.isNotEmpty() && (depths[sessionId] ?: 0) > 0

    fun enter(sessionId: String) {
        if (sessionId.isEmpty()) return
        depths.compute(sessionId) { _, n -> (n ?: 0) + 1 }
    }

    fun leave(sessionId: String) {
        if (sessionId.isEmpty()) return
        depths.compute(sessionId) { _, n ->
            val next = (n ?: 0) - 1
            if (next <= 0) null else next
        }
    }

    suspend fun <T> withHeadless(sessionId: String, block: suspend () -> T): T {
        enter(sessionId)
        try {
            return block()
        } finally {
            leave(sessionId)
        }
    }

    fun resetForTest() {
        depths.clear()
    }
}
