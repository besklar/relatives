package com.besklar.relatives.ui.list

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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
class PeopleListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val records = FakeRecords()
    private val person = PersonSummary("A-1", PersonName("Ada", "Whitcomb"), "female", false,
        LifeEvent("about 1838", 1838, "Nauvoo"), LifeEvent("1903", 1903, "Ogden"), "portraits/A-1.jpg")
    private fun snapshot(people: List<PersonSummary> = listOf(person), skipped: Int = 0) =
        PeopleSnapshot(people, "2026-08-14", 1234, skipped)
    private fun viewModel() = PeopleListViewModel(records).also { store.put("list", it) }

    @Before fun start() = Dispatchers.setMain(dispatcher)
    @After fun stop() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test fun savedPeopleAppearWhileRefreshIsStillPending() = runTest(dispatcher) {
        records.saved.value = snapshot()
        val gate = CompletableDeferred<RefreshResult>()
        records.refresh = { gate.await() }
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(snapshot(), vm.state.value.snapshot)
        assertFalse(vm.state.value.readingStore)
        assertTrue(vm.state.value.refreshing)
        gate.complete(RefreshResult.NetworkFailure)
        advanceUntilIdle()
        assertEquals(snapshot(), vm.state.value.snapshot)
        assertEquals(RefreshResult.NetworkFailure, vm.state.value.failure)
        assertFalse(vm.state.value.refreshing)
    }

    @Test fun firstLaunchFailureCanRetryAndPopulateSavedModels() = runTest(dispatcher) {
        records.refresh = { RefreshResult.NetworkFailure }
        val vm = viewModel()
        advanceUntilIdle()
        assertNull(vm.state.value.snapshot)
        assertEquals(RefreshResult.NetworkFailure, vm.state.value.failure)
        records.refresh = { records.saved.value = snapshot(); RefreshResult.Success() }
        vm.refresh()
        advanceUntilIdle()
        assertEquals(snapshot(), vm.state.value.snapshot)
        assertNull(vm.state.value.failure)
        assertEquals(1, vm.state.value.portraitGeneration)
    }

    @Test fun successfulEmptyListIsStoredAndRetainedIfNextRefreshFails() = runTest(dispatcher) {
        records.refresh = { records.saved.value = snapshot(emptyList()); RefreshResult.Success() }
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.state.value.snapshot!!.people.isEmpty())
        records.refresh = { RefreshResult.InvalidData }
        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.state.value.snapshot!!.people.isEmpty())
        assertEquals(RefreshResult.InvalidData, vm.state.value.failure)
    }

    @Test fun persistedSkippedCountSurvivesFailedRefresh() = runTest(dispatcher) {
        records.saved.value = snapshot(skipped = 2)
        records.refresh = { RefreshResult.HttpFailure(503) }
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(2, vm.state.value.snapshot!!.discardedRecordCount)
        assertEquals(RefreshResult.HttpFailure(503), vm.state.value.failure)
    }

    @Test fun repeatedRefreshActionsDoNotQueueDuplicateRequests() = runTest(dispatcher) {
        val gate = CompletableDeferred<RefreshResult>()
        records.refresh = { gate.await() }
        val vm = viewModel()
        advanceUntilIdle()
        repeat(4) { vm.refresh() }
        advanceUntilIdle()
        assertEquals(1, records.requests)
        gate.complete(RefreshResult.Success())
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(2, records.requests)
    }

    @Test fun clearingViewModelCancelsRequestWithoutPublishingFailure() = runTest(dispatcher) {
        val gate = CompletableDeferred<RefreshResult>()
        var canceled = false
        records.refresh = { try { gate.await() } finally { canceled = true } }
        val vm = viewModel()
        advanceUntilIdle()
        store.clear()
        advanceUntilIdle()
        assertTrue(canceled)
        assertNull(vm.state.value.failure)
        assertFalse(vm.state.value.refreshing)
    }

    @Test fun initialDiskReadIsNotMistakenForAnUncachedFailure() = runTest(dispatcher) {
        records.readGate = CompletableDeferred()
        records.saved.value = snapshot()
        records.refresh = { RefreshResult.NetworkFailure }
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.state.value.readingStore)
        records.readGate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.state.value.readingStore)
        assertEquals(snapshot(), vm.state.value.snapshot)
    }

    @Test fun livingAndUnknownDeathHaveHonestLifespans() {
        assertEquals("1838 – 1903", person.lifespan("Living"))
        assertEquals("1838 – Living", person.copy(living = true, death = null).lifespan("Living"))
        assertEquals("1838 – ?", person.copy(death = null).lifespan("Living"))
    }

    private class FakeRecords : PersonRecords {
        val saved = MutableStateFlow<PeopleSnapshot?>(null)
        var refresh: suspend () -> RefreshResult = { RefreshResult.Success() }
        var readGate: CompletableDeferred<Unit>? = null
        var requests = 0
        override fun observePeople(): Flow<PeopleSnapshot?> = flow {
            readGate?.await()
            emitAll(saved)
        }
        override fun observeProfile(id: String): Flow<PersonProfile?> = flowOf(null)
        override suspend fun refreshPeople(): RefreshResult { requests++; return refresh() }
        override suspend fun refreshProfile(id: String): RefreshResult = error("Not used by list")
    }
}
