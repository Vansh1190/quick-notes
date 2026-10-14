package com.vansh.quicknotes.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vansh.quicknotes.data.local.QuickNotesDatabase
import com.vansh.quicknotes.domain.reminder.ReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListsRepositoryTest {

    private class FakeReminders : ReminderScheduler {
        val scheduled = mutableMapOf<String, Long>()
        val cancelled = mutableListOf<String>()
        override fun schedule(itemId: String, itemText: String, dueAt: Long) { scheduled[itemId] = dueAt }
        override fun cancel(itemId: String) { cancelled += itemId }
        override suspend fun rescheduleAll() = Unit
    }

    private lateinit var db: QuickNotesDatabase
    private lateinit var repo: ListsRepository
    private lateinit var reminders: FakeReminders

    @Before
    fun setUp() {
        db = androidx.room.Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            QuickNotesDatabase::class.java,
        )
            .allowMainThreadQueries()
            .setQueryExecutor(java.util.concurrent.Executor { it.run() })
            .setTransactionExecutor(java.util.concurrent.Executor { it.run() })
            .build()
        db.clearAllTables()
        reminders = FakeReminders()
        repo = ListsRepository(db.todoDao(), reminders)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun createListWithItemsAndProgress() = runTest {
        val id = repo.createList("Groceries", items = listOf("milk", "eggs", "bread"))
        repo.setDone(repo.observeList(id).first().second[0].id, true)
        val (list, items) = repo.observeList(id).first()
        assertEquals("Groceries", list.title)
        assertEquals(3, items.size)
        val summary = repo.observeSummaries().first().first { it.list.id == id }
        assertEquals(2, summary.openCount)
        assertTrue(summary.progress > 0.3f && summary.progress < 0.4f)
    }

    @Test
    fun dueDateSchedulesReminderAndCancelClearsIt() = runTest {
        val id = repo.createList("Work")
        val itemId = repo.addItem(id, "send invoice")
        val due = System.currentTimeMillis() + 3_600_000
        repo.setDue(itemId, due)
        assertEquals(due, reminders.scheduled[itemId])

        repo.setDue(itemId, null)
        assertTrue(itemId in reminders.cancelled)

        val items = repo.observeList(id).first().second
        assertNull(items.first().dueAt)
    }

    @Test
    fun completingAnItemCancelsItsReminder() = runTest {
        val id = repo.createList("Work")
        val itemId = repo.addItem(id, "call bank")
        repo.setDue(itemId, System.currentTimeMillis() + 1_800_000)
        repo.setDone(itemId, true)
        assertTrue(itemId in reminders.cancelled)
    }

    @Test
    fun removeItemDeletesAndCancels() = runTest {
        val id = repo.createList("Personal")
        val itemId = repo.addItem(id, "call mom")
        repo.setDue(itemId, System.currentTimeMillis() + 60_000)
        repo.removeItem(itemId)
        assertTrue(repo.observeList(id).first().second.isEmpty())
        assertTrue(itemId in reminders.cancelled)
    }

    @Test
    fun reorderChangesOrderIndex() = runTest {
        val id = repo.createList("Groceries", items = listOf("a", "b", "c"))
        val items = repo.observeList(id).first().second
        repo.reorder(id, items.map { it.id }.reversed())
        val after = repo.observeList(id).first().second.map { it.text }
        assertEquals(listOf("c", "b", "a"), after)
    }

    @Test
    fun purgeDoneKeepsOpenItems() = runTest {
        val id = repo.createList("Errands", items = listOf("done-1", "open-1", "done-2"))
        val items = repo.observeList(id).first().second
        repo.setDone(items[0].id, true)
        repo.setDone(items[2].id, true)
        repo.purgeDone(id)
        val after = repo.observeList(id).first().second
        assertEquals(1, after.size)
        assertEquals("open-1", after.first().text)
    }

    @Test
    fun existingListMatchByTitle() = runTest {
        repo.createList("Client work")
        assertNotNull(repo.findByTitle("client WORK"))
        assertNull(repo.findByTitle("nothing"))
    }
}
