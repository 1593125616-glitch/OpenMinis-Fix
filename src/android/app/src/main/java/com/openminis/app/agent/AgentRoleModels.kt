package com.openminis.app.agent

import android.content.Context

/**
 * Optional dedicated model-entry ids for cheap side jobs (title, memory
 * extract, compact/summary). Empty means "use the existing sub-group /
 * primary provider path".
 */
object AgentRoleModels {
    private const val PREFS = "minis_agent_roles"
    const val ROLE_TITLE = "title"
    const val ROLE_MEMORY = "memory"
    const val ROLE_SUMMARY = "summary"

    fun getEntryId(context: Context, role: String): String =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(role, "") ?: ""

    fun setEntryId(context: Context, role: String, entryId: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(role, entryId.trim()).apply()
    }
}
