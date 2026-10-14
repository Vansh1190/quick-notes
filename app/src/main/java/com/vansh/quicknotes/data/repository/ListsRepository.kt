package com.vansh.quicknotes.data.repository

import com.vansh.quicknotes.data.local.ListProgress
import com.vansh.quicknotes.data.local.TodoDao
import com.vansh.quicknotes.data.local.TodoItemEntity
import com.vansh.quicknotes.data.local.TodoListEntity
import com.vansh.quicknotes.domain.reminder.ReminderScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class TodoList(
    val id: String,
    val title: String,
    val createdAt: Long,
    val source: String,
    val items: List<TodoItem> = emptyList(),
    val progress: Float = 0f,
)

data class TodoItem(
    val id: String,
    val text: String,
    val done: Boolean = false,
    val dueAt: Long? = null,
    val parentId: String? = null,
    val orderIndex: Int = 0,
)

@Singleton
class ListsRepository @Inject constructor(
    private val dao: TodoDao,
    private val reminderScheduler: ReminderScheduler,
) {
    private val now: () -> Long = System::currentTimeMillis

    data class ListSummary(
        val list: TodoListEntity,
        val progress: Float,
        val openCount: Int,
        val dueSoon: Int,
    )

    fun observeSummaries(): Flow<List<ListSummary>> = kotlinx.coroutines.flow.combine(
        dao.observeLists(),
        dao.observeProgress(),
    ) { lists, progress ->
        val byId = progress.associateBy { it.listId }
        lists.map { list ->
            val p = byId[list.id]
            ListSummary(
                list = list,
                progress = p?.progress?.toFloat() ?: 0f,
                openCount = p?.openCount ?: 0,
                dueSoon = 0,
            )
        }
    }

    fun observeList(id: String): Flow<Pair<TodoListEntity, List<TodoItemEntity>>> =
        kotlinx.coroutines.flow.combine(
            flow = dao.observeItems(id),
            flow2 = kotlinx.coroutines.flow.flow { emit(dao.listById(id)) },
        ) { items, listEntity ->
            (listEntity ?: TodoListEntity(id, "", now(), "manual")) to items
        }

    suspend fun createList(title: String, source: String = "manual", items: List<String> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        dao.upsertList(TodoListEntity(id = id, title = title.trim(), createdAt = now(), source = source))
        if (items.isNotEmpty()) {
            dao.upsertItems(items.mapIndexed { i, text -> TodoItemEntity(UUID.randomUUID().toString(), id, text.trim(), orderIndex = i) })
        }
        return id
    }

    suspend fun addItem(listId: String, text: String): String {
        val order = (dao.maxOrder(listId) ?: -1) + 1
        val id = UUID.randomUUID().toString()
        dao.upsertItem(TodoItemEntity(id, listId, text.trim(), orderIndex = order))
        return id
    }

    suspend fun setDone(itemId: String, done: Boolean) {
        val existing = dao.itemById(itemId) ?: return
        dao.upsertItem(existing.copy(done = done))
        if (done) reminderScheduler.cancel(itemId)
    }

    suspend fun setDue(itemId: String, dueAt: Long?) {
        val existing = dao.itemById(itemId) ?: return
        dao.upsertItem(existing.copy(dueAt = dueAt))
        if (dueAt == null) reminderScheduler.cancel(itemId) else reminderScheduler.schedule(itemId, existing.text, dueAt)
    }

    suspend fun removeItem(itemId: String) {
        val existing = dao.itemById(itemId) ?: return
        dao.deleteItem(existing)
        reminderScheduler.cancel(itemId)
    }

    suspend fun renameItem(itemId: String, text: String) {
        val existing = dao.itemById(itemId) ?: return
        dao.upsertItem(existing.copy(text = text.trim()))
    }

    suspend fun reorder(listId: String, orderedIds: List<String>) {
        val items = dao.itemsOnce(listId).associateBy { it.id }
        dao.upsertItems(orderedIds.mapIndexedNotNull { i, id -> items[id]?.copy(orderIndex = i) })
    }

    suspend fun replaceItems(listId: String, items: List<TodoItemEntity>) = dao.replaceItems(listId, items)

    suspend fun listById(id: String) = dao.listById(id)

    suspend fun titleMatches(query: String): List<TodoListEntity> = dao.listsOnce().filter {
        it.title.contains(query.trim(), ignoreCase = true)
    }

    suspend fun purgeDone(listId: String) = dao.deleteDoneIn(listId)

    suspend fun deleteList(list: TodoListEntity) {
        dao.deleteList(list)
    }

    suspend fun allTitles(): List<String> = dao.listsOnce().map { it.title }

    suspend fun findByTitle(title: String): TodoListEntity? =
        dao.listsOnce().firstOrNull { it.title.equals(title.trim(), ignoreCase = true) }

    suspend fun appendItems(listId: String, texts: List<String>) {
        var order = (dao.maxOrder(listId) ?: -1) + 1
        texts.filter { it.isNotBlank() }.forEach { text ->
            dao.upsertItem(TodoItemEntity(UUID.randomUUID().toString(), listId, text.trim(), orderIndex = order))
            order += 1
        }
    }

}
