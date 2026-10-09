package com.besklar.relatives.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.besklar.relatives.data.PersonRecords
import com.besklar.relatives.data.RefreshResult
import com.besklar.relatives.model.PersonProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileState(
    val profile: PersonProfile? = null,
    val readingStore: Boolean = true,
    val refreshing: Boolean = false,
    val failure: RefreshResult? = null,
    val portraitGeneration: Int = 0,
)

class ProfileViewModel(val personId: String, private val records: PersonRecords) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfileState())
    val state = mutableState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            records.observeProfile(personId).collect { profile ->
                mutableState.update { it.copy(profile = profile, readingStore = false) }
            }
        }
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        mutableState.update { it.copy(refreshing = true, failure = null) }
        refreshJob = viewModelScope.launch {
            try {
                val result = records.refreshProfile(personId)
                mutableState.update {
                    if (result is RefreshResult.Success) {
                        it.copy(failure = null, portraitGeneration = it.portraitGeneration + 1)
                    } else it.copy(failure = result)
                }
            } finally {
                mutableState.update { it.copy(refreshing = false) }
            }
        }
    }
}
