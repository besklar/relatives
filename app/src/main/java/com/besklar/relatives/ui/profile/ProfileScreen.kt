package com.besklar.relatives.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.besklar.relatives.ui.portrait.PortraitViewer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.besklar.relatives.R
import com.besklar.relatives.model.LifeEvent
import com.besklar.relatives.model.Relative
import com.besklar.relatives.ui.*
import com.besklar.relatives.ui.list.lifespan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(state: ProfileState, onRefresh: () -> Unit, onBack: () -> Unit,
    onRelativeClick: (String) -> Unit, portraits: PortraitLoader,
    preview: ProfilePreview? = null,
    onOpenPreview: ((ProfilePreview, String) -> Unit)? = null,
    portraitModifier: @Composable (String, String) -> Modifier = { _, _ -> Modifier }) {
    val personId = state.profile?.person?.id ?: preview?.id
    var viewingPortrait by rememberSaveable(personId) { mutableStateOf(false) }
    val profile = state.profile
    val identity = profile?.person?.let {
        ProfilePreview(it.id, it.name.fullName, it.lifespan(stringResource(R.string.living)), it.portraitUrl)
    } ?: preview
    BackHandler(enabled = viewingPortrait) { viewingPortrait = false }
    if (viewingPortrait && identity != null) {
        PortraitViewer(identity.portraitPath, identity.name, portraits) { viewingPortrait = false }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.profile)) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
            }
        }, actions = { RefreshAction(state.refreshing, onRefresh) })
    }) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.fillMaxSize().testTag("profile-content"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (identity != null) item(key = "identity") {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val openPortraitLabel = stringResource(R.string.open_portrait)
                        PersonPortrait(identity.portraitPath, portraits, state.portraitGeneration,
                            portraitModifier(identity.id, "hero").semantics { contentDescription = openPortraitLabel }.clickable(
                                onClickLabel = openPortraitLabel,
                                role = androidx.compose.ui.semantics.Role.Button) { viewingPortrait = true }, 160.dp)
                        Text(identity.name, style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Text(identity.lifespan, style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                when {
                    state.readingStore || (profile == null && (state.refreshing || state.failure == null)) -> item {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.loading_profile))
                        }
                    }
                    profile == null -> item {
                        Card(shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(R.string.could_not_load_profile), style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.no_saved_profile))
                                Text(failureText(state.failure))
                                TextButton(onClick = onRefresh) { Text(stringResource(R.string.try_again)) }
                            }
                        }
                    }
                    else -> {
                        item(key = "status") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(savedAtText(profile.retrievedAt), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                RefreshFeedback(state.failure, state.refreshing, onRefresh)
                            }
                        }
                        item(key = "life") {
                            ProfileSection(stringResource(R.string.life_details)) {
                                LifeEventSection(stringResource(R.string.birth), profile.person.birth)
                                if (profile.person.living) Text(stringResource(R.string.living))
                                else {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    LifeEventSection(stringResource(R.string.death), profile.person.death)
                                }
                            }
                        }
                        item(key = "occupation") {
                            ProfileSection(stringResource(R.string.occupation)) {
                                Text(profile.occupation?.takeIf(String::isNotBlank) ?: stringResource(R.string.not_recorded))
                            }
                        }
                        item(key = "biography") {
                            ProfileSection(stringResource(R.string.biography)) {
                                Text(profile.biography.takeIf(String::isNotBlank) ?: stringResource(R.string.not_recorded),
                                    style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        item(key = "relatives-heading") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionHeading(stringResource(R.string.relatives))
                                if (profile.discardedRelativeCount > 0) Text(pluralStringResource(R.plurals.skipped_relatives,
                                    profile.discardedRelativeCount, profile.discardedRelativeCount))
                                if (profile.relatives.isEmpty()) Text(stringResource(R.string.no_relatives))
                            }
                        }
                        items(profile.relatives, key = { "${it.id}:${it.relationship}" }) { relative ->
                            val path = state.relativePortraitPaths[relative.id].orEmpty()
                            val relation = relationshipText(relative.relationship)
                            val slot = "relative:${relative.id}:${relative.relationship}"
                            PersonCard(relative.name.fullName, "$relation · ${relative.lifespan()}", null, path,
                                portraits, state.portraitGeneration, onClick = {
                                    if (onOpenPreview != null) onOpenPreview(
                                        ProfilePreview(relative.id, relative.name.fullName, relative.lifespan(), path), slot)
                                    else onRelativeClick(relative.id)
                                }, portraitModifier = portraitModifier(relative.id, slot))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading(title)
            content()
        }
    }
}

@Composable
private fun LifeEventSection(title: String, event: LifeEvent?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (event == null) Text(stringResource(R.string.not_recorded))
        else {
            Text(event.date, style = MaterialTheme.typography.bodyLarge)
            Text(event.place, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
