package com.openminis.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionUndoTest {
    @Test
    fun `undo drops last user and after`() {
        val msgs = listOf(
            SessionUndo.Msg("user"),
            SessionUndo.Msg("assistant"),
            SessionUndo.Msg("user"),
            SessionUndo.Msg("assistant"),
        )
        val cut = SessionUndo.undoLastTurn(msgs)!!
        assertEquals(2, cut.keepCount)
        assertEquals(2, cut.droppedCount)
    }

    @Test
    fun `undo empty`() {
        assertNull(SessionUndo.undoLastTurn(emptyList()))
    }

    @Test
    fun `branch keeps prefix`() {
        val msgs = listOf(SessionUndo.Msg("user"), SessionUndo.Msg("assistant"), SessionUndo.Msg("user"))
        val cut = SessionUndo.branchAt(msgs, 1)!!
        assertEquals(2, cut.keepCount)
        assertEquals(1, cut.droppedCount)
    }
}
