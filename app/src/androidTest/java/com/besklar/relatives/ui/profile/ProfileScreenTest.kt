package com.besklar.relatives.ui.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.besklar.relatives.data.PersonRecords
import com.besklar.relatives.data.RefreshResult
import com.besklar.relatives.model.LifeEvent
import com.besklar.relatives.model.PeopleSnapshot
import com.besklar.relatives.model.PersonName
import com.besklar.relatives.model.PersonProfile
import com.besklar.relatives.model.PersonSummary
import com.besklar.relatives.model.Relative
import com.besklar.relatives.ui.PortraitLoader
import com.besklar.relatives.ui.RelativesNavigation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfileScreenTest {
    @get:Rule val compose = createComposeRule()
    private val person = PersonSummary("A-1", PersonName("Ada", "Whitcomb"), "female", false,
        LifeEvent("about 1838", 1838, "Nauvoo"), LifeEvent("1903", 1903, "Ogden"), "portraits/A-1.jpg")
    private val relative = Relative("B-2", "father", PersonName("Amos", "Whitcomb"), 1810, 1877)
    private val profile = PersonProfile(person, null, "Saved story.", listOf(relative), "today", 1234)
    private val portraits = PortraitLoader { _, _ -> null }
    private fun show(state: ProfileState, back: () -> Unit = {}, retry: () -> Unit = {},
        open: (String) -> Unit = {}) {
        compose.setContent { MaterialTheme { ProfileScreen(state, retry, back, open, portraits) } }
    }

    @Test fun profileShowsRequestedDetailsAndRelativeClickUsesId() {
        var opened: String? = null
        show(ProfileState(profile, readingStore = false), open = { opened = it })
        compose.onNodeWithText("Ada Whitcomb").assertIsDisplayed()
        compose.onNodeWithText("about 1838").assertIsDisplayed()
        compose.onNodeWithText("Nauvoo").assertIsDisplayed()
        compose.onNodeWithTag("profile-content").performScrollToNode(hasText("Not recorded"))
        compose.onNodeWithText("Not recorded").assertIsDisplayed()
        compose.onNodeWithTag("profile-content").performScrollToNode(hasText("Saved story."))
        compose.onNodeWithText("Saved story.").assertIsDisplayed()
        compose.onNodeWithTag("profile-content").performScrollToNode(hasText("Amos Whitcomb"))
        compose.onNodeWithText("Amos Whitcomb").performClick()
        compose.runOnIdle { assertEquals("B-2", opened) }
    }

    @Test fun livingProfileDoesNotInventDeathDetails() {
        show(ProfileState(profile.copy(person = person.copy(living = true, death = null)), readingStore = false))
        compose.onNodeWithTag("profile-content").performScrollToNode(hasText("Living"))
        compose.onNodeWithText("Living").assertIsDisplayed()
        compose.onNodeWithText("Death").assertDoesNotExist()
    }

    @Test fun emptyAndSkippedRelativesAreExplicit() {
        show(ProfileState(profile.copy(relatives = emptyList(), discardedRelativeCount = 2), readingStore = false))
        compose.onNodeWithTag("profile-content").performScrollToNode(hasText("No relatives recorded."))
        compose.onNodeWithText("No relatives recorded.").assertIsDisplayed()
        compose.onNodeWithText("2 relatives couldn’t be read and were skipped.").assertIsDisplayed()
    }

    @Test fun unopenedOfflineProfileHasRetryAndBack() {
        var retried = 0
        var backed = 0
        show(ProfileState(readingStore = false, failure = RefreshResult.NetworkFailure),
            back = { backed++ }, retry = { retried++ })
        compose.onNodeWithText("No saved profile is available.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(1, retried); assertEquals(1, backed) }
    }

    @Test fun loadingHasVisibleProgressMessage() {
        show(ProfileState(refreshing = true))
        compose.onNodeWithText("Loading profile…").assertIsDisplayed()
        compose.onNodeWithText("No saved profile is available.").assertDoesNotExist()
    }

    @Test fun savedFamilyNavigationWorksWhenEveryRefreshFailsAndBackRetainsList() {
        val parent = profile.copy(person = person.copy(id = "B-2", name = relative.name),
            relatives = listOf(Relative("A-1", "daughter", person.name, 1838, 1903)))
        val records = CachedRecords(mapOf("A-1" to profile, "B-2" to parent))
        compose.setContent { MaterialTheme { RelativesNavigation(records, portraits) } }
        compose.onNodeWithText("Ada Whitcomb").performClick()
        compose.onNodeWithText("No saved profile is available.").assertDoesNotExist()
        compose.onNodeWithTag("profile-content").performScrollToNode(hasText("Amos Whitcomb"))
        compose.onNodeWithText("Amos Whitcomb").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { records.requested.contains("B-2") }
        compose.onNodeWithTag("profile-content").assertIsDisplayed()
        compose.onNodeWithText("Amos Whitcomb").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("A-1", "B-2"), records.requested) }
        compose.onNodeWithTag("profile-content").performScrollToNode(hasText("Ada Whitcomb"))
        compose.onNodeWithText("Ada Whitcomb").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { records.requested.size == 3 }
        compose.runOnIdle { assertEquals(listOf("A-1", "B-2", "A-1"), records.requested) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("people-list").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, records.listRefreshes) }
    }

    private inner class CachedRecords(private val profiles: Map<String, PersonProfile>) : PersonRecords {
        override fun observeRelativePortraitPaths(ownerId: String): Flow<Map<String, String>> = kotlinx.coroutines.flow.flowOf(emptyMap())
        val requested = mutableListOf<String>()
        var listRefreshes = 0
        override fun observePeople(): Flow<PeopleSnapshot?> =
            flowOf(PeopleSnapshot(listOf(person), "today", 1234, 0))
        override fun observeProfile(id: String): Flow<PersonProfile?> = flowOf(profiles[id])
        override suspend fun refreshPeople(): RefreshResult { listRefreshes++; return RefreshResult.NetworkFailure }
        override suspend fun refreshProfile(id: String): RefreshResult { requested += id; return RefreshResult.NetworkFailure }
    }
}
