package com.besklar.relatives.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.besklar.relatives.R
import com.besklar.relatives.model.LifeEvent
import com.besklar.relatives.model.Relative
import com.besklar.relatives.ui.PersonPortrait
import com.besklar.relatives.ui.PortraitLoader
import com.besklar.relatives.ui.failureText
import com.besklar.relatives.ui.list.lifespan
import com.besklar.relatives.ui.savedAtText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(state: ProfileState, onRefresh: () -> Unit, onBack: () -> Unit,
    onRelativeClick: (String) -> Unit, portraits: PortraitLoader) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.profile)) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
            }
        }, actions = {
            TextButton(onClick = onRefresh, enabled = !state.refreshing) { Text(stringResource(R.string.refresh)) }
        })
    }) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding)) {
            val profile = state.profile
            when {
                state.readingStore || (profile == null && (state.refreshing || state.failure == null)) -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.loading_profile))
                        }
                    }
                }
                profile == null -> {
                    Column(Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(stringResource(R.string.could_not_load_profile), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.no_saved_profile), Modifier.padding(vertical = 12.dp))
                        Text(failureText(state.failure))
                        TextButton(onClick = onRefresh) { Text(stringResource(R.string.try_again)) }
                    }
                }
                else -> {
                    LazyColumn(Modifier.fillMaxSize().testTag("profile-content")) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (state.failure != null) {
                                    Text(failureText(state.failure), color = MaterialTheme.colorScheme.error)
                                    TextButton(onClick = onRefresh, enabled = !state.refreshing) {
                                        Text(stringResource(R.string.try_again))
                                    }
                                }
                                Text(savedAtText(profile.retrievedAt), style = MaterialTheme.typography.bodySmall)
                                PersonPortrait(profile.person.portraitUrl, portraits, state.portraitGeneration,
                                    Modifier.align(Alignment.CenterHorizontally), 160.dp)
                                Text(profile.person.name.fullName, style = MaterialTheme.typography.headlineMedium)
                                Text(profile.person.lifespan(stringResource(R.string.living)))
                                LifeEventSection(stringResource(R.string.birth), profile.person.birth)
                                if (profile.person.living) {
                                    Text(stringResource(R.string.living), style = MaterialTheme.typography.titleMedium)
                                } else {
                                    LifeEventSection(stringResource(R.string.death), profile.person.death)
                                }
                                Text(stringResource(R.string.occupation), style = MaterialTheme.typography.titleMedium)
                                Text(profile.occupation?.takeIf(String::isNotBlank) ?: stringResource(R.string.not_recorded))
                                Text(stringResource(R.string.biography), style = MaterialTheme.typography.titleMedium)
                                Text(profile.biography.takeIf(String::isNotBlank) ?: stringResource(R.string.not_recorded))
                                Text(stringResource(R.string.relatives), style = MaterialTheme.typography.titleLarge)
                                if (profile.discardedRelativeCount > 0) {
                                    Text(pluralStringResource(R.plurals.skipped_relatives,
                                        profile.discardedRelativeCount, profile.discardedRelativeCount))
                                }
                                if (profile.relatives.isEmpty()) Text(stringResource(R.string.no_relatives))
                            }
                        }
                        items(profile.relatives, key = { "${it.id}:${it.relationship}" }) { relative ->
                            Column(Modifier.fillMaxWidth().clickable { onRelativeClick(relative.id) }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(relative.name.fullName, style = MaterialTheme.typography.titleMedium)
                                Text(relationshipText(relative.relationship))
                                Text(relative.lifespan(), style = MaterialTheme.typography.bodySmall)
                            }
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LifeEventSection(title: String, event: LifeEvent?) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (event == null) Text(stringResource(R.string.not_recorded))
    else {
        Text(event.date)
        Text(event.place)
    }
}

fun Relative.lifespan(): String = "$birthYear – ${deathYear?.toString() ?: "?"}"

@Composable
private fun relationshipText(relationship: String): String = when (relationship) {
    "father" -> stringResource(R.string.father)
    "mother" -> stringResource(R.string.mother)
    "spouse" -> stringResource(R.string.spouse)
    "son" -> stringResource(R.string.son)
    "daughter" -> stringResource(R.string.daughter)
    else -> relationship
}
