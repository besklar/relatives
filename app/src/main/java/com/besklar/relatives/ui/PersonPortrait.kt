package com.besklar.relatives.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.besklar.relatives.R

internal val LocalPortraitTransitionActive = compositionLocalOf { false }

@Composable
fun PersonPortrait(path: String, portraits: PortraitLoader, generation: Int,
    modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val pixels = with(LocalDensity.current) { size.roundToPx() }
    val state = remember(path, portraits) { mutableStateOf(portraits.peek(path)) }
    val transitionActive = rememberUpdatedState(LocalPortraitTransitionActive.current)
    LaunchedEffect(path, pixels, generation, portraits) {
        val loaded = portraits.load(path, pixels)
        // Keep the displayed source decode through the shared bounds animation.
        snapshotFlow { transitionActive.value }.first { !it }
        state.value = loaded ?: state.value
    }
    val bitmap by state
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
