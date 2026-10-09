package com.besklar.relatives.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.besklar.relatives.data.PersonRecords
import com.besklar.relatives.data.RefreshResult
import com.besklar.relatives.model.PeopleSnapshot
import com.besklar.relatives.model.PersonSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PeopleListState(
    val snapshot: PeopleSnapshot? = null,
    val readingStore: Boolean = true,
    val refreshing: Boolean = false,
    val failure: RefreshResult? = null,
    val portraitGeneration: Int = 0,
)

class PeopleListViewModel(private val repository: PersonRecords) : ViewModel() {
    private val mutableState = MutableStateFlow(PeopleListState())
    val state = mutableState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observePeople().collect { snapshot ->
                mutableState.update { it.copy(snapshot = snapshot, readingStore = false) }
            }
        }
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        mutableState.update { it.copy(refreshing = true, failure = null) }
        refreshJob = viewModelScope.launch {
            try {
                val result = repository.refreshPeople()
                mutableState.update {
                    if (result is RefreshResult.Success) {
                        it.copy(failure = null, portraitGeneration = it.portraitGeneration + 1)
                    } else {
                        it.copy(failure = result)
                    }
                }
            } finally {
                mutableState.update { it.copy(refreshing = false) }
            }
        }
    }
}

fun PersonSummary.lifespan(livingLabel: String): String =
    "${birth.year} – ${if (living) livingLabel else death?.year?.toString() ?: "?"}"
