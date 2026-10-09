package com.besklar.relatives.ui.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.besklar.relatives.R
import com.besklar.relatives.data.RefreshResult
import com.besklar.relatives.model.PersonSummary
import com.besklar.relatives.ui.PortraitLoader
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleListScreen(state: PeopleListState, onRefresh: () -> Unit, portraits: PortraitLoader) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.app_name)) }, actions = {
            TextButton(onClick = onRefresh, enabled = !state.refreshing) {
                Text(stringResource(R.string.refresh))
            }
        })
    }) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
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
                snapshot == null -> {
                    CenteredMessage(stringResource(R.string.could_not_load_people),
                        failureText(state.failure), stringResource(R.string.try_again), onRefresh)
                }
                else -> {
                    LazyColumn(Modifier.fillMaxSize().testTag("people-list")) {
                        item {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(pluralStringResource(R.plurals.people_count, snapshot.people.size, snapshot.people.size),
                                    style = MaterialTheme.typography.titleMedium)
                                val saved = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(snapshot.retrievedAt))
                                Text(stringResource(R.string.saved_at, saved),
                                    style = MaterialTheme.typography.bodySmall)
                                if (snapshot.discardedRecordCount > 0) {
                                    Text(pluralStringResource(R.plurals.skipped_people,
                                        snapshot.discardedRecordCount, snapshot.discardedRecordCount),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                if (state.failure != null) {
                                    Text(failureText(state.failure), color = MaterialTheme.colorScheme.error)
                                    TextButton(onClick = onRefresh, enabled = !state.refreshing) {
                                        Text(stringResource(R.string.try_again))
                                    }
                                }
                            }
                        }
                        if (snapshot.people.isEmpty()) {
                            item {
                                CenteredMessage(stringResource(R.string.no_people),
                                    stringResource(R.string.no_people_description), stringResource(R.string.refresh),
                                    onRefresh, Modifier.fillMaxWidth().padding(vertical = 48.dp), !state.refreshing)
                            }
                        }
                        items(snapshot.people, key = { it.id }) { person ->
                            PersonRow(person, portraits, state.portraitGeneration)
                            HorizontalDivider(Modifier.padding(start = 88.dp, end = 16.dp))
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

@Composable
private fun PersonRow(person: PersonSummary, portraits: PortraitLoader, generation: Int) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        PersonPortrait(person.portraitUrl, portraits, generation)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(person.name.fullName, style = MaterialTheme.typography.titleMedium)
            Text(person.lifespan(stringResource(R.string.living)), style = MaterialTheme.typography.bodyMedium)
            Text(person.birth.place, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun PersonPortrait(path: String, portraits: PortraitLoader, generation: Int, modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 56.dp) {
    val pixels = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<android.graphics.Bitmap?>(null, path, pixels, generation) {
        value = null
        value = portraits.load(path, pixels)
    }
    val frame = modifier.size(size).clip(CircleShape)
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), contentDescription = null,
            modifier = frame.testTag("portrait:$path"), contentScale = ContentScale.Crop)
    } else {
        Box(frame.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.portrait_placeholder), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun failureText(failure: RefreshResult?): String = stringResource(when (failure) {
    RefreshResult.NetworkFailure -> R.string.network_failure
    is RefreshResult.HttpFailure -> R.string.service_failure
    RefreshResult.StorageFailure -> R.string.storage_failure
    else -> R.string.records_failure
})
