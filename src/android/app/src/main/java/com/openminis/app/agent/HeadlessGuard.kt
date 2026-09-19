package com.openminis.app.agent

import java.util.concurrent.atomic.AtomicInteger

/**
 * Telegram / local HTTP / subagent run through HeadlessChatRunner.
 * ASK_ONCE must not wait on a dialog those callers cannot see.
 */
object HeadlessGuard {
    private val depth = AtomicInteger(0)

    fun inHeadless(): Boolean = depth.get() > 0

    fun enter() { depth.incrementAndGet() }

    fun leave() { depth.updateAndGet { (it - 1).coerceAtLeast(0) } }

    inline fun <T> run(block: () -> T): T {
        enter()
        try {
            return block()
        } finally {
            leave()
        }
    }

    suspend fun <T> withHeadless(block: suspend () -> T): T {
        enter()
        try {
            return block()
        } finally {
            leave()
        }
    }
}
