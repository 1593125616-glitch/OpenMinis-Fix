package com.openminis.app.agent

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/** Process-wide Plan/Agent default. Per-chat override lives on ChatViewModel. */
object AgentModeStore {
    private const val PREFS = "minis_agent_loop"
    private const val KEY = "mode"

    private val _mode = MutableStateFlow(AgentMode.AGENT)
    val mode: StateFlow<AgentMode> = _mode.asStateFlow()
    private val forcePlan = AtomicInteger(0)

    fun init(context: Context) {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, AgentMode.AGENT.name)
        _mode.value = AgentLoopPolicy.parse(raw)
    }

    fun current(): AgentMode =
        if (com.openminis.app.tools.SubagentTool.inChild() || forcePlan.get() > 0) {
            AgentMode.PLAN
        } else {
            _mode.value
        }

    fun set(context: Context, mode: AgentMode) {
        _mode.value = mode
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, mode.name).apply()
    }

    inline fun <T> withPlan(block: () -> T): T {
        forcePlanBump()
        try {
            return block()
        } finally {
            forcePlanDrop()
        }
    }

    fun forcePlanBump() { forcePlan.incrementAndGet() }
    fun forcePlanDrop() { forcePlan.decrementAndGet() }
}
