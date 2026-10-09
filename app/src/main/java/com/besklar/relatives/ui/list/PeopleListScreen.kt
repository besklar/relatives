package com.besklar.relatives.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.besklar.relatives.R
import com.besklar.relatives.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleListScreen(state: PeopleListState, onRefresh: () -> Unit, portraits: PortraitLoader,
    onPersonClick: (String) -> Unit = {},
    onOpenPreview: ((ProfilePreview, String) -> Unit)? = null,
    portraitModifier: @Composable (String, String) -> Modifier = { _, _ -> Modifier }) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.app_name)) }, actions = {
            RefreshAction(state.refreshing, onRefresh)
        })
    }) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding)) {
            val snapshot = state.snapshot
            when {
                state.readingStore || (snapshot == null && (state.refreshing || state.failure == null)) -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.loading_people))
                        }
                    }
                }
                snapshot == null -> CenteredMessage(stringResource(R.string.could_not_load_people),
                    failureText(state.failure), stringResource(R.string.try_again), onRefresh)
                else -> {
                    LazyColumn(Modifier.fillMaxSize().testTag("people-list"),
                        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionHeading(pluralStringResource(R.plurals.people_count, snapshot.people.size, snapshot.people.size))
                                Text(savedAtText(snapshot.retrievedAt), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (snapshot.discardedRecordCount > 0) Text(pluralStringResource(R.plurals.skipped_people,
                                    snapshot.discardedRecordCount, snapshot.discardedRecordCount),
                                    style = MaterialTheme.typography.bodySmall)
                                RefreshFeedback(state.failure, state.refreshing, onRefresh)
                            }
                        }
                        if (snapshot.people.isEmpty()) item {
                            CenteredMessage(stringResource(R.string.no_people), stringResource(R.string.no_people_description),
                                stringResource(R.string.refresh), onRefresh,
                                Modifier.fillMaxWidth().padding(vertical = 48.dp), !state.refreshing)
                        }
                        items(snapshot.people, key = { it.id }) { person ->
                            val life = person.lifespan(stringResource(R.string.living))
                            val slot = "list:${person.id}"
                            PersonCard(person.name.fullName, life, person.birth.place, person.portraitUrl,
                                portraits, state.portraitGeneration, onClick = {
                                    if (onOpenPreview != null) onOpenPreview(
                                        ProfilePreview(person.id, person.name.fullName, life, person.portraitUrl), slot)
                                    else onPersonClick(person.id)
                                }, portraitModifier = portraitModifier(person.id, slot))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(title: String, description: String, action: String, onAction: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(), enabled: Boolean = true) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, Modifier.padding(vertical = 12.dp))
        TextButton(onClick = onAction, enabled = enabled) { Text(action) }
    }
}
