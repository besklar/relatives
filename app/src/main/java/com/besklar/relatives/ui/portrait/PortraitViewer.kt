package com.besklar.relatives.ui.portrait

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.besklar.relatives.R
import com.besklar.relatives.ui.PortraitLoader
import kotlin.math.min

@Composable
fun PortraitViewer(path: String, name: String, portraits: PortraitLoader, onDismiss: () -> Unit) {
    var bitmap by remember(path, portraits) { mutableStateOf(portraits.peek(path)) }
    var loading by remember(path) { mutableStateOf(true) }
    var retry by remember(path) { mutableIntStateOf(0) }
    LaunchedEffect(path, retry, portraits) {
        loading = true
        try {
            bitmap = portraits.loadFullSize(path, 2048) ?: bitmap
        } finally { loading = false }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false, dismissOnClickOutside = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            // The viewer is always dark, including when the app theme is light.
            controller?.isAppearanceLightStatusBars = false
            controller?.isAppearanceLightNavigationBars = false
            onDispose { } // The separate dialog window is destroyed when dismissed.
        }
        Column(Modifier.fillMaxSize().background(Color(0xFF101014)).safeDrawingPadding().testTag("portrait-viewer")) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close_portrait), tint = Color.White)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val image = bitmap
                if (image != null) {
                    var viewport by remember { mutableStateOf(IntSize.Zero) }
                    var transform by remember(path, viewport) { mutableStateOf(PhotoTransform()) }
                    val fit = min(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)
                    val fitted = Size(image.width * fit, image.height * fit)
                    LaunchedEffect(fitted) {
                        transform = transform.bounded(viewport.width.toFloat(), viewport.height.toFloat(), fitted.width, fitted.height)
                    }
                    val zoomInLabel = stringResource(R.string.zoom_in)
                    val zoomOutLabel = stringResource(R.string.zoom_out)
                    val resetLabel = stringResource(R.string.reset_zoom)
                    fun zoom(factor: Float) {
                        transform = transform.gesture(factor, 0f, 0f, 0f, 0f,
                            viewport.width.toFloat(), viewport.height.toFloat(), fitted.width, fitted.height)
                    }
                    Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { viewport = it }
                        .testTag("zoomable-portrait")
                        .semantics {
                            stateDescription = "${(transform.scale * 100).toInt()}%"
                            customActions = listOf(
                                CustomAccessibilityAction(zoomInLabel) { zoom(1.5f); true },
                                CustomAccessibilityAction(zoomOutLabel) { zoom(1f / 1.5f); true },
                                CustomAccessibilityAction(resetLabel) { transform = PhotoTransform(); true })
                        }
                        .pointerInput(viewport, fitted) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                transform = transform.gesture(zoom, pan.x, pan.y,
                                    centroid.x - viewport.width / 2f, centroid.y - viewport.height / 2f,
                                    viewport.width.toFloat(), viewport.height.toFloat(), fitted.width, fitted.height)
                            }
                        }) {
                        Image(image.asImageBitmap(), contentDescription = name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = transform.scale; scaleY = transform.scale
                                translationX = transform.x; translationY = transform.y
                            })
                    }
                } else if (loading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator(color = Color.White)
                        Text(stringResource(R.string.loading_portrait), color = Color.White)
                    }
                } else {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.portrait_unavailable), color = Color.White)
                        TextButton(onClick = { retry++ }) { Text(stringResource(R.string.try_again), color = Color.White) }
                    }
                }
            }
            if (bitmap != null) Text(stringResource(R.string.pinch_to_zoom), color = Color(0xFFCBC4D5),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally).padding(12.dp))
        }
    }
}
