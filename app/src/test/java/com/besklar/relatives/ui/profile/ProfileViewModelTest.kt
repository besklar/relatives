package com.besklar.relatives.ui.profile

import androidx.lifecycle.ViewModelStore
import com.besklar.relatives.data.PersonRecords
import com.besklar.relatives.data.RefreshResult
import com.besklar.relatives.model.LifeEvent
import com.besklar.relatives.model.PeopleSnapshot
import com.besklar.relatives.model.PersonName
import com.besklar.relatives.model.PersonProfile
import com.besklar.relatives.model.PersonSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val records = FakeRecords()
    private val profile = PersonProfile(PersonSummary("A-1", PersonName("Ada", "Whitcomb"), "female", true,
        LifeEvent("1838", 1838, "Nauvoo"), null, "portraits/A-1.jpg"), null, "A story", emptyList(), "today", 1234, 2)
    private fun viewModel() = ProfileViewModel("A-1", records)
        .also { store.put("profile", it) }
    @Before fun start() = Dispatchers.setMain(dispatcher)
    @After fun stop() { store.clear(); Dispatchers.resetMain() }

    @Test fun relativePhotoPathsUpdateWithoutFetchingOtherProfiles() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        records.paths.value = mapOf("B-2" to "portraits/B-2.jpg")
        advanceUntilIdle()
        assertEquals(records.paths.value, vm.state.value.relativePortraitPaths)
        assertEquals(listOf("A-1"), records.requested)
        assertNull(vm.state.value.profile)
    }

    @Test fun savedProfileAppearsWhileIdSpecificRefreshIsPending() = runTest(dispatcher) {
        records.saved.value = profile
        val gate = CompletableDeferred<RefreshResult>()
        records.refresh = { gate.await() }
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals("A-1", vm.personId)
        assertEquals(listOf("A-1"), records.requested)
        assertEquals("A-1", records.observedId)
        assertEquals(profile, vm.state.value.profile)
        assertTrue(vm.state.value.refreshing)
        gate.complete(RefreshResult.NetworkFailure)
        advanceUntilIdle()
        assertEquals(profile, vm.state.value.profile)
        assertEquals(RefreshResult.NetworkFailure, vm.state.value.failure)
    }

    @Test fun unopenedOfflineProfileHasFailureAndRetryCanPopulateIt() = runTest(dispatcher) {
        records.refresh = { RefreshResult.NetworkFailure }
        val vm = viewModel()
        advanceUntilIdle()
        assertNull(vm.state.value.profile)
        assertFalse(vm.state.value.readingStore)
        assertEquals(RefreshResult.NetworkFailure, vm.state.value.failure)
        records.refresh = { records.saved.value = profile; RefreshResult.Success(2) }
        vm.refresh()
        advanceUntilIdle()
        assertEquals(profile, vm.state.value.profile)
        assertEquals(2, vm.state.value.profile!!.discardedRelativeCount)
        assertNull(vm.state.value.failure)
        assertEquals(1, vm.state.value.portraitGeneration)
    }

    @Test fun duplicateActionsDoNotQueueProfileRequests() = runTest(dispatcher) {
        val gate = CompletableDeferred<RefreshResult>()
        records.refresh = { gate.await() }
        val vm = viewModel()
        advanceUntilIdle()
        repeat(4) { vm.refresh() }
        advanceUntilIdle()
        assertEquals(1, records.requested.size)
        gate.complete(RefreshResult.Success())
    }

    @Test fun removingEntryCancelsPendingRefreshWithoutErrorState() = runTest(dispatcher) {
        var canceled = false
        records.refresh = { try { CompletableDeferred<RefreshResult>().await() } finally { canceled = true } }
        val vm = viewModel()
        advanceUntilIdle()
        store.clear()
        advanceUntilIdle()
        assertTrue(canceled)
        assertNull(vm.state.value.failure)
        assertFalse(vm.state.value.refreshing)
    }

    private class FakeRecords : PersonRecords {
        val paths = MutableStateFlow<Map<String, String>>(emptyMap())
        override fun observeRelativePortraitPaths(ownerId: String): Flow<Map<String, String>> = paths
        val saved = MutableStateFlow<PersonProfile?>(null)
        val requested = mutableListOf<String>()
        var observedId: String? = null
        var refresh: suspend () -> RefreshResult = { RefreshResult.Success() }
        override fun observePeople(): Flow<PeopleSnapshot?> = flowOf(null)
        override fun observeProfile(id: String): Flow<PersonProfile?> { observedId = id; return saved }
        override suspend fun refreshPeople(): RefreshResult = error("Not used by profile")
        override suspend fun refreshProfile(id: String): RefreshResult { requested += id; return refresh() }
    }
}
