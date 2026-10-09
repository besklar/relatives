package com.besklar.relatives.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.besklar.relatives.data.PortraitResult
import com.besklar.relatives.data.PortraitStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

fun interface PortraitLoader {
    suspend fun load(path: String, targetPixels: Int): Bitmap?
}

class SavedPortraitLoader(private val store: PortraitStore) : PortraitLoader {
    override suspend fun load(path: String, targetPixels: Int): Bitmap? = withContext(Dispatchers.IO) {
        val result = store.load(path)
        if (result !is PortraitResult.Ready) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(result.file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        val target = targetPixels.coerceAtLeast(1)
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) {
            sample *= 2
        }
        ensureActive()
        val decoded = BitmapFactory.decodeFile(result.file.path,
            BitmapFactory.Options().apply { inSampleSize = sample })
        try {
            ensureActive()
            decoded
        } catch (failure: kotlinx.coroutines.CancellationException) {
            decoded?.recycle()
            throw failure
        }
    }
}
