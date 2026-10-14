package com.vansh.quicknotes.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Upsert
    suspend fun upsertList(list: TodoListEntity)

    @Delete
    suspend fun deleteList(list: TodoListEntity)

    @Query("SELECT * FROM todo_lists WHERE archived = 0 ORDER BY createdAt DESC")
    fun observeLists(): Flow<List<TodoListEntity>>

    @Query("SELECT * FROM todo_lists WHERE id = :id")
    suspend fun listById(id: String): TodoListEntity?

    @Upsert
    suspend fun upsertItems(items: List<TodoItemEntity>)

    @Upsert
    suspend fun upsertItem(item: TodoItemEntity)

    @Delete
    suspend fun deleteItem(item: TodoItemEntity)

    @Query("SELECT * FROM todo_items WHERE listId = :listId ORDER BY orderIndex ASC")
    suspend fun itemsOnce(listId: String): List<TodoItemEntity>

    @Query("SELECT * FROM todo_items WHERE id = :id")
    suspend fun itemById(id: String): TodoItemEntity?

    @Query("SELECT * FROM todo_lists ORDER BY createdAt DESC")
    suspend fun listsOnce(): List<TodoListEntity>

    @Query("DELETE FROM todo_items WHERE listId = :listId AND done = 1")
    suspend fun deleteDoneIn(listId: String)

    @Query("SELECT * FROM todo_items WHERE dueAt IS NOT NULL AND done = 0 AND dueAt > :now ORDER BY dueAt ASC")
    suspend fun upcomingDue(now: Long): List<TodoItemEntity>

    @Query("SELECT * FROM todo_items WHERE listId = :listId ORDER BY orderIndex ASC")
    fun observeItems(listId: String): Flow<List<TodoItemEntity>>

    @Query(
        """
        SELECT * FROM todo_items
        WHERE dueAt IS NOT NULL AND done = 0 AND dueAt BETWEEN :from AND :to
        ORDER BY dueAt ASC
        """,
    )
    suspend fun dueBetween(from: Long, to: Long): List<TodoItemEntity>

    @Query("SELECT * FROM todo_items WHERE dueAt IS NOT NULL AND done = 0 AND dueAt < :now")
    fun observeOverdue(now: Long): Flow<List<TodoItemEntity>>

    /** per-list progress aggregation without loading every item. */
    @Query(
        """
        SELECT listId,
               SUM(CASE WHEN done = 1 THEN 1 ELSE 0 END) * 1.0 / COUNT(*) AS progress,
               COUNT(*) AS total,
               SUM(CASE WHEN done = 0 THEN 1 ELSE 0 END) AS openCount
        FROM todo_items
        GROUP BY listId
        """,
    )
    fun observeProgress(): Flow<List<ListProgress>>

    @Query("SELECT MAX(orderIndex) FROM todo_items WHERE listId = :listId")
    suspend fun maxOrder(listId: String): Int?

    @Transaction
    suspend fun replaceItems(listId: String, items: List<TodoItemEntity>) {
        val existingIds = itemsOnce(listId).map { it.id }
        val keep = items.map { it.id }.toSet()
        val doomed = existingIds.filterNot { it in keep }
        if (doomed.isNotEmpty()) {
            deleteItemsByIds(doomed)
        }
        upsertItems(items.mapIndexed { i, item -> item.copy(orderIndex = i) })
    }

    @Query("DELETE FROM todo_items WHERE id IN (:ids)")
    suspend fun deleteItemsByIds(ids: List<String>)
}

/** Plain query-result POJO (not an @Entity — projection of an aggregate). */
data class ListProgress(
    val listId: String,
    val progress: Double,
    val total: Int,
    val openCount: Int,
)
