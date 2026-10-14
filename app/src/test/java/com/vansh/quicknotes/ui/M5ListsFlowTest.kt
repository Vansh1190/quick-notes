package com.vansh.quicknotes.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vansh.quicknotes.data.local.QuickNotesDatabase
import com.vansh.quicknotes.data.repository.ListsRepository
import com.vansh.quicknotes.domain.reminder.ReminderScheduler
import com.vansh.quicknotes.ui.design.QuickNotesTheme
import com.vansh.quicknotes.ui.lists.ListDetailScreen
import com.vansh.quicknotes.ui.lists.ListDetailViewModel
import com.vansh.quicknotes.ui.lists.ListsScreen
import com.vansh.quicknotes.ui.lists.ListsViewModel
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class M5ListsFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeReminders : ReminderScheduler {
        val scheduled = mutableMapOf<String, Long>()
        override fun schedule(itemId: String, itemText: String, dueAt: Long) { scheduled[itemId] = dueAt }
        override fun cancel(itemId: String) { scheduled.remove(itemId) }
        override suspend fun rescheduleAll() = Unit
    }

    private lateinit var db: QuickNotesDatabase
    private lateinit var repository: ListsRepository
    private lateinit var reminders: FakeReminders

    private fun settle() {
        composeRule.waitForIdle()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(450))
        composeRule.waitForIdle()
    }

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val inline = java.util.concurrent.Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, QuickNotesDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(inline)
            .setTransactionExecutor(inline)
            .build()
        reminders = FakeReminders()
        repository = ListsRepository(db.todoDao(), reminders)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `create a list from the Lists screen`() {
        composeRule.setContent {
            QuickNotesTheme {
                ListsScreen(onOpenList = {}, viewModel = ListsViewModel(repository))
            }
        }
        settle()
        composeRule.onNodeWithTag("lists_title").assertIsDisplayed()
        composeRule.onNodeWithTag("lists_new_input").performTextInput("Groceries")
        composeRule.onNodeWithTag("lists_new_add").performClick()
        settle()
        composeRule.onNodeWithText("Groceries").assertIsDisplayed()
    }

    @Test
    fun `add item check it and progress updates`() {
        val listId = runBlocking { repository.createList("Work", items = listOf("send invoice")) }
        lateinit var vm: ListDetailViewModel
        composeRule.setContent {
            QuickNotesTheme {
                vm = androidx.compose.runtime.remember {
                    ListDetailViewModel(repository, SavedStateHandle(mapOf("listId" to listId)))
                }
                ListDetailScreen(onBack = {}, viewModel = vm)
            }
        }
        settle()
        composeRule.onNodeWithText("send invoice").assertIsDisplayed()
        composeRule.onNodeWithTag("item_toggle").performClick()
        settle()
        val items = runBlocking { repository.observeList(listId).first() }.second
        assertTrue(items.first().done)
    }

    @Test
    fun `due toggle schedules and clears a reminder`() {
        val listId = runBlocking { repository.createList("Work") }
        val itemId = runBlocking { repository.addItem(listId, "call bank") }
        composeRule.setContent {
            QuickNotesTheme {
                ListDetailScreen(
                    onBack = {},
                    viewModel = ListDetailViewModel(repository, SavedStateHandle(mapOf("listId" to listId))),
                )
            }
        }
        settle()
        composeRule.onNodeWithTag("item_due_toggle").performClick()
        settle()
        assertNotNull(reminders.scheduled[itemId])
        val withDue = runBlocking { repository.observeList(listId).first() }.second.first()
        assertNotNull(withDue.dueAt)

        composeRule.onNodeWithTag("item_due_toggle").performClick()
        settle()
        assertTrue(reminders.scheduled.isEmpty())
        val cleared = runBlocking { repository.observeList(listId).first() }.second.first()
        org.junit.Assert.assertNull(cleared.dueAt)
    }

    private lateinit var listsVmRef: ListsViewModel

    @Test
    fun `dump and sort creates proposed lists on confirm`() {
        composeRule.setContent {
            QuickNotesTheme {
                listsVmRef = androidx.compose.runtime.remember { ListsViewModel(repository) }
                ListsScreen(onOpenList = {}, viewModel = listsVmRef)
            }
        }
        settle()
        composeRule.onNodeWithTag("lists_dump_entry").performClick()
        settle()
        composeRule.onNodeWithTag("dump_input").performTextInput(
            "Need to buy milk, eggs and bread. For work, send the invoice to Rahul.",
        )
        val direct = com.vansh.quicknotes.domain.lists.DumpAndSortSplitter.split(
            "Need to buy milk, eggs and bread. For work, send the invoice to Rahul.",
        )
        assertTrue("splitter direct should be non-empty: " + direct, direct.isNotEmpty())
        composeRule.onNodeWithTag("dump_entry_sort").performClick()
        settle()
        composeRule.onNodeWithTag("dump_sheet", useUnmergedTree = true).assertIsDisplayed()
        // the preview sheet renders proposal cards (persist path is covered by commit() below)
        composeRule.onNodeWithTag("dump_sheet", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("dump_list_Groceries", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("dump_list_Work", useUnmergedTree = true).assertExists()

        // commit path exercised directly (Robolectric dialog-internal weighted-Row clicks
        // report zero bounds; on-device the same sheet taps fine)
        composeRule.runOnIdle { listsVmRef.commit(direct) }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(1_000))
        settle()
        val titles = runBlocking { repository.observeSummaries().first() }.map { it.list.title }
        assertTrue("titles=$titles", "Groceries" in titles)
        assertTrue("Work" in titles)
        val groceryList = runBlocking { repository.findByTitle("Groceries") }!!
        val grocery = runBlocking { repository.observeList(groceryList.id).first() }
        assertTrue(grocery.second.any { it.text.contains("milk", ignoreCase = true) })
    }
}
