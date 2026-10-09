package com.besklar.relatives.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.besklar.relatives.R

@Composable
fun PersonPortrait(path: String, portraits: PortraitLoader, generation: Int,
    modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val pixels = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<Bitmap?>(null, path, pixels, generation) {
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
