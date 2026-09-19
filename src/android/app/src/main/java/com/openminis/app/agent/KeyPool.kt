package com.openminis.app.agent

import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Extra API keys per provider instance. [pick] round-robins across
 * primary + extras; [markFailed] skips a key for a while after 401/429.
 */
object KeyPool {
    private const val PREFIX = "keypool_"
    private val index = ConcurrentHashMap<String, AtomicInteger>()
    private val failedUntil = ConcurrentHashMap<String, Long>()
    private val lastPick = ConcurrentHashMap<String, String>()
    private const val FAIL_COOLDOWN_MS = 5 * 60 * 1000L

    fun extras(prefs: SharedPreferences, instanceId: String): List<String> {
        val raw = prefs.getString(PREFIX + instanceId, "") ?: ""
        return parse(raw)
    }

    fun setExtras(prefs: SharedPreferences, instanceId: String, raw: String) {
        prefs.edit().putString(PREFIX + instanceId, parse(raw).joinToString("\n")).apply()
    }

    fun parse(raw: String): List<String> =
        raw.split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.length >= 8 }

    fun pick(instanceId: String, primary: String?, extras: List<String>): String? {
        val keys = buildList {
            if (!primary.isNullOrBlank()) add(primary)
            addAll(extras)
        }.distinct()
        if (keys.isEmpty()) return primary
        val now = System.currentTimeMillis()
        val live = keys.filter { (failedUntil[failKey(instanceId, it)] ?: 0L) < now }
        val last = lastPick[instanceId]
        if (last != null && last in live) return last
        val pool = live.ifEmpty { keys }
        val n = index.getOrPut(instanceId) { AtomicInteger(0) }
        val i = (n.getAndIncrement() and Int.MAX_VALUE) % pool.size
        val chosen = pool[i]
        lastPick[instanceId] = chosen
        return chosen
    }

    fun lastPicked(instanceId: String): String? = lastPick[instanceId]

    fun markFailed(instanceId: String, key: String) {
        failedUntil[failKey(instanceId, key)] = System.currentTimeMillis() + FAIL_COOLDOWN_MS
    }

    fun markLastFailed(instanceId: String) {
        lastPick.remove(instanceId)?.let { markFailed(instanceId, it) }
    }

    private fun failKey(instanceId: String, key: String) =
        instanceId + ":" + key.take(8) + key.takeLast(4)
}
