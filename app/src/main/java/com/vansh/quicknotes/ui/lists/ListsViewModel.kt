package com.vansh.quicknotes.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vansh.quicknotes.data.local.TodoListEntity
import com.vansh.quicknotes.data.repository.ListsRepository
import com.vansh.quicknotes.domain.lists.DumpAndSortSplitter
import com.vansh.quicknotes.domain.lists.ProposedList
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ListsUiState(
    val summaries: List<ListsRepository.ListSummary> = emptyList(),
)

@HiltViewModel
open class ListsViewModel @Inject constructor(
    private val repository: ListsRepository,
) : ViewModel() {

    val state: StateFlow<ListsUiState> = repository.observeSummaries()
        .map { ListsUiState(summaries = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListsUiState())

    fun createList(title: String, onCreated: (String) -> Unit = {}) {
        if (title.isBlank()) return
        viewModelScope.launch { onCreated(repository.createList(title)) }
    }

    /** Dump & Sort preview (heuristic stub until the M6 LLM pass). */
    fun propose(text: String, onDone: (List<ProposedList>) -> Unit) {
        viewModelScope.launch {
            val existing = repository.allTitles()
            onDone(DumpAndSortSplitter.split(text, existing))
        }
    }

    open fun commit(proposals: List<ProposedList>) {
        viewModelScope.launch {
            proposals.forEach { proposal ->
                val existingTitle = proposal.items.firstNotNullOfOrNull { it.existingListTitle }
                val existingId = existingTitle?.let { repository.findByTitle(it) }?.id
                if (existingId != null) {
                    repository.appendItems(existingId, proposal.items.map { it.text })
                } else {
                    val id = repository.createList(proposal.title, source = "voice")
                    proposal.items.forEach { item ->
                        val itemId = repository.addItem(id, item.text)
                        item.dueAt?.let { repository.setDue(itemId, it) }
                    }
                }
            }
        }
    }

    fun deleteList(list: TodoListEntity) {
        viewModelScope.launch { repository.deleteList(list) }
    }
}
