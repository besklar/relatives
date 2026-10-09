package com.besklar.relatives.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.besklar.relatives.data.PortraitResult
import com.besklar.relatives.data.PortraitStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

fun interface PortraitLoader {
    suspend fun load(path: String, targetPixels: Int): Bitmap?
    fun peek(path: String): Bitmap? = null
    suspend fun loadFullSize(path: String, maxDimension: Int): Bitmap? = load(path, maxDimension)
}

class SavedPortraitLoader(private val store: PortraitStore) : PortraitLoader {
    private val memory = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    override fun peek(path: String): Bitmap? = synchronized(memory) {
        memory.get(path)?.takeUnless { it.isRecycled }
    }

    override suspend fun load(path: String, targetPixels: Int): Bitmap? = loadImage(path, targetPixels, false)
    override suspend fun loadFullSize(path: String, maxDimension: Int): Bitmap? = loadImage(path, maxDimension, true)

    private suspend fun loadImage(path: String, targetPixels: Int, capLongestSide: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext null
        val cached = peek(path)
        if (!capLongestSide && cached != null && minOf(cached.width, cached.height) >= targetPixels) {
            return@withContext cached
        }
        val result = store.load(path)
        if (result !is PortraitResult.Ready) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(result.file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        val target = targetPixels.coerceAtLeast(1)
        if (capLongestSide) {
            while ((maxOf(bounds.outWidth, bounds.outHeight).toLong() + sample - 1) / sample > target) sample *= 2
            val expectedWidth = (bounds.outWidth.toLong() + sample - 1) / sample
            val expectedHeight = (bounds.outHeight.toLong() + sample - 1) / sample
            if (cached != null && cached.width.toLong() == expectedWidth && cached.height.toLong() == expectedHeight) {
                return@withContext cached
            }
        } else {
            while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
        }
        ensureActive()
        val decoded = BitmapFactory.decodeFile(result.file.path,
            BitmapFactory.Options().apply { inSampleSize = sample })
        try {
            ensureActive()
            if (decoded != null) synchronized(memory) {
                val previous = memory.get(path)
                if (decoded.allocationByteCount <= memory.maxSize() &&
                    (previous == null || previous.isRecycled || decoded.width > previous.width)) {
                    memory.put(path, decoded)
                }
            }
            decoded
        } catch (failure: kotlinx.coroutines.CancellationException) {
            decoded?.recycle()
            throw failure
        }
    }
}
