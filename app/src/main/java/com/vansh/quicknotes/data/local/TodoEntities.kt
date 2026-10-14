package com.vansh.quicknotes.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Top-level todo list (spec §7 / §11: TodoList). In-note checklists stay as block payloads. */
@Entity(tableName = "todo_lists")
data class TodoListEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    /** manual | voice */
    val source: String = "manual",
    val archived: Boolean = false,
)

/** ChecklistItem with optional list membership (spec §11: blockId|listId, dueAt, nesting). */
@Entity(
    tableName = "todo_items",
    foreignKeys = [
        ForeignKey(TodoListEntity::class, ["id"], ["listId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("listId"), Index("dueAt")],
)
data class TodoItemEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val text: String,
    val done: Boolean = false,
    val dueAt: Long? = null,
    /** one-level nesting: parent must be a top-level item in the same list */
    val parentId: String? = null,
    val orderIndex: Int = 0,
)
