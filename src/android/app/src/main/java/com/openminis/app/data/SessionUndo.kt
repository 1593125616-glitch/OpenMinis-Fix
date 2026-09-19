package com.openminis.app.data

/**
 * Decide how far to rewind a linear transcript. Pure.
 *
 * Undo last turn: drop the last user message and everything after it
 * (assistant + tool results).
 * Branch: keep prefix through [keepThroughIndex] inclusive.
 */
data class TranscriptCut(
    val keepCount: Int,
    val droppedCount: Int,
)

object SessionUndo {
    data class Msg(val role: String, val isToolResult: Boolean = false)

    fun undoLastTurn(messages: List<Msg>): TranscriptCut? {
        val lastUser = messages.indexOfLast { it.role == "user" && !it.isToolResult }
        if (lastUser < 0) return null
        val keep = lastUser
        return TranscriptCut(keepCount = keep, droppedCount = messages.size - keep)
    }

    fun branchAt(messages: List<Msg>, index: Int): TranscriptCut? {
        if (index !in messages.indices) return null
        val keep = index + 1
        return TranscriptCut(keepCount = keep, droppedCount = messages.size - keep)
    }
}
