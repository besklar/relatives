package com.besklar.relatives.ui.list

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.besklar.relatives.data.RefreshResult
import com.besklar.relatives.model.LifeEvent
import com.besklar.relatives.model.PeopleSnapshot
import com.besklar.relatives.model.PersonName
import com.besklar.relatives.model.PersonSummary
import com.besklar.relatives.ui.PortraitLoader
import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PeopleListScreenTest {
    @get:Rule val compose = createComposeRule()
    private val person = PersonSummary("A-1", PersonName("Ada", "Whitcomb"), "female", false,
        LifeEvent("about 1838", 1838, "Nauvoo, Hancock, Illinois, United States"),
        LifeEvent("1903", 1903, "Ogden"), "portraits/A-1.jpg")
    private val portraits = PortraitLoader { _, _ -> null }
    private fun show(state: PeopleListState, refresh: () -> Unit = {}) {
        compose.setContent { MaterialTheme { PeopleListScreen(state, refresh, portraits) } }
    }

    @Test fun populatedListShowsAllRequiredRowText() {
        show(PeopleListState(PeopleSnapshot(listOf(person), "2026-08-14", 1234, 0), readingStore = false))
        compose.onNodeWithText("Ada Whitcomb").assertIsDisplayed()
        compose.onNodeWithText("1838 – 1903").assertIsDisplayed()
        compose.onNodeWithText(person.birth.place).assertIsDisplayed()
        compose.onNodeWithText("1 person").assertIsDisplayed()
        compose.onNodeWithText("?").assertIsDisplayed()
    }

    @Test fun loadingDoesNotShowEmptyOrFailure() {
        show(PeopleListState(refreshing = true))
        compose.onNodeWithText("Loading people…").assertIsDisplayed()
        compose.onNodeWithText("No people yet").assertDoesNotExist()
        compose.onNodeWithText("Couldn’t load people").assertDoesNotExist()
    }

    @Test fun uncachedFailureProvidesRetry() {
        var retries = 0
        show(PeopleListState(readingStore = false, failure = RefreshResult.NetworkFailure)) { retries++ }
        compose.onNodeWithText("Couldn’t load people").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun emptySuccessfulSnapshotShowsEmptyMessage() {
        show(PeopleListState(PeopleSnapshot(emptyList(), "2026-08-14", 1234, 0), readingStore = false))
        compose.onNodeWithText("No people yet").assertIsDisplayed()
        compose.onNodeWithText("Couldn’t load people").assertDoesNotExist()
    }

    @Test fun cachedFailureRetainsPeopleAndDisclosesSkippedRecords() {
        var retries = 0
        show(PeopleListState(PeopleSnapshot(listOf(person), "2026-08-14", 1234, 2),
            readingStore = false, failure = RefreshResult.NetworkFailure)) { retries++ }
        compose.onNodeWithText("Ada Whitcomb").assertIsDisplayed()
        compose.onNodeWithText("2 records couldn’t be read and were skipped.").assertIsDisplayed()
        compose.onNodeWithText("Couldn’t connect. Check your connection and try again.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun successfulRefreshRetriesPreviouslyUnavailablePortrait() {
        var loads = 0
        val loader = PortraitLoader { _, _ -> loads++; null }
        val state = mutableStateOf(PeopleListState(
            PeopleSnapshot(listOf(person), "2026-08-14", 1234, 0), readingStore = false))
        compose.setContent { MaterialTheme { PeopleListScreen(state.value, {}, loader) } }
        compose.runOnIdle { assertEquals(1, loads); state.value = state.value.copy(portraitGeneration = 1) }
        compose.runOnIdle { assertEquals(2, loads) }
    }
}
