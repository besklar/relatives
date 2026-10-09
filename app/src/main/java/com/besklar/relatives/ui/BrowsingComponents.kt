package com.besklar.relatives.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.besklar.relatives.R
import com.besklar.relatives.data.RefreshResult

// Display-only navigation hint. Full records still come exclusively from Room.
data class ProfilePreview(val id: String, val name: String, val lifespan: String, val portraitPath: String)

@Composable
fun RefreshAction(refreshing: Boolean, onRefresh: () -> Unit) {
    IconButton(onClick = onRefresh, enabled = !refreshing) {
        Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.refresh))
    }
}

@Composable
fun SectionHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() })
}

@Composable
fun RefreshFeedback(failure: RefreshResult?, refreshing: Boolean, onRefresh: () -> Unit) {
    if (failure != null) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(failureText(failure), color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRefresh, enabled = !refreshing) { Text(stringResource(R.string.try_again)) }
            }
        }
    }
}

@Composable
fun PersonCard(name: String, subtitle: String, detail: String?, path: String,
    portraits: PortraitLoader, generation: Int, onClick: () -> Unit,
    modifier: Modifier = Modifier, portraitModifier: Modifier = Modifier) {
    val actionLabel = stringResource(R.string.view_profile, name)
    Card(onClick = onClick, modifier = modifier.fillMaxWidth().semantics {
        onClick(label = actionLabel, action = null)
    }, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PersonPortrait(path, portraits, generation, portraitModifier, 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_chevron), contentDescription = null,
                tint = MaterialTheme.colorScheme.primary)
        }
    }
}
