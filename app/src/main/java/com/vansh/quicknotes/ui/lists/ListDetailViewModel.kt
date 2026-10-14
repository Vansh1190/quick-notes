package com.vansh.quicknotes.ui.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vansh.quicknotes.data.local.TodoItemEntity
import com.vansh.quicknotes.data.local.TodoListEntity
import com.vansh.quicknotes.data.repository.ListsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ListDetailUiState(
    val list: TodoListEntity? = null,
    val items: List<TodoItemEntity> = emptyList(),
    val progress: Float = 0f,
)

@HiltViewModel
class ListDetailViewModel @Inject constructor(
    private val repository: ListsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val listId: String = savedStateHandle.get<String>("listId") ?: ""

    val state: StateFlow<ListDetailUiState> = repository.observeList(listId)
        .map { (list, items) ->
            ListDetailUiState(
                list = list,
                items = items,
                progress = if (items.isEmpty()) 0f else items.count { it.done }.toFloat() / items.size,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListDetailUiState())

    fun add(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { repository.addItem(listId, text) }
    }

    fun setDone(itemId: String, done: Boolean) {
        viewModelScope.launch { repository.setDone(itemId, done) }
    }

    fun setDue(itemId: String, dueAt: Long?) {
        viewModelScope.launch { repository.setDue(itemId, dueAt) }
    }

    fun remove(itemId: String) {
        viewModelScope.launch { repository.removeItem(itemId) }
    }

    fun rename(itemId: String, text: String) {
        viewModelScope.launch { repository.renameItem(itemId, text) }
    }

    fun reorder(orderedIds: List<String>) {
        viewModelScope.launch { repository.reorder(listId, orderedIds) }
    }
}
