package com.vansh.quicknotes.domain

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoSaveTest {

    @Test
    fun `rapid edits coalesce into a single save`() = runTest {
        var saves = 0
        val autoSave = AutoSave(this, delayMillis = 500) { saves++ }
        for (i in 0 until 10) {
            autoSave.edit()
            advanceTimeBy(50)
        }
        assertEquals(0, saves) // still debouncing
        advanceTimeBy(600)
        assertEquals(1, saves)
    }

    @Test
    fun `flush forces save immediately and clears dirty state`() = runTest {
        var saves = 0
        val autoSave = AutoSave(this, delayMillis = 500) { saves++ }
        autoSave.edit()
        autoSave.flush()
        assertEquals(1, saves)
        autoSave.flush() // nothing dirty anymore
        assertEquals(1, saves)
        advanceTimeBy(600) // pending debounce already cancelled/short-circuited by flush
        assertEquals(1, saves)
    }

    @Test
    fun `edit without changes never saves`() = runTest {
        var saves = 0
        val autoSave = AutoSave(this, delayMillis = 10) { saves++ }
        advanceTimeBy(100)
        assertEquals(0, saves)
    }
}
