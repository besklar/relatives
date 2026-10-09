package com.besklar.relatives.data

import android.graphics.BitmapFactory
import com.besklar.relatives.data.remote.RECORDS_BASE_URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface PortraitResult {
    data class Ready(val file: File) : PortraitResult
    data class Unavailable(val reason: Reason) : PortraitResult
    enum class Reason { INVALID_PATH, NETWORK, HTTP, INVALID_IMAGE, STORAGE }
}

private class InvalidPortrait : Exception()

class PortraitStore(
    private val directory: File,
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = RECORDS_BASE_URL,
    private val isReadableImage: (File) -> Boolean = ::readableImage,
    private val maxBytes: Long = 8_000_000,
) {
    private val mutex = Mutex()

    suspend fun load(relativePath: String): PortraitResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val url = baseUrl.resolve(relativePath)
            if (relativePath.isBlank() || url == null || url.scheme != baseUrl.scheme ||
                url.host != baseUrl.host || url.port != baseUrl.port ||
                url.username.isNotEmpty() || url.password.isNotEmpty()) {
                return@withLock PortraitResult.Unavailable(PortraitResult.Reason.INVALID_PATH)
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(url.toString().toByteArray(Charsets.UTF_8))
            val key = digest.joinToString("") { "%02x".format(it) }
            val saved = File(directory, "$key.image")
            try {
                if (saved.isFile && isReadableImage(saved)) return@withLock PortraitResult.Ready(saved)
                if (!directory.isDirectory && !directory.mkdirs()) {
                    return@withLock PortraitResult.Unavailable(PortraitResult.Reason.STORAGE)
                }
                val temporary = File.createTempFile(key, ".tmp", directory)
                download(url, saved, temporary)
            } catch (_: IOException) {
                PortraitResult.Unavailable(PortraitResult.Reason.STORAGE)
            }
        }
    }

    // All streaming happens on OkHttp's worker. Cancellation closes the HTTP call,
    // and no file is published until the entire response is validated.
    private suspend fun download(url: HttpUrl, saved: File, temporary: File): PortraitResult =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(url).build())
            continuation.invokeOnCancellation {
                call.cancel()
                temporary.delete()
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    temporary.delete()
                    if (continuation.isActive) {
                        continuation.resume(PortraitResult.Unavailable(PortraitResult.Reason.NETWORK))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    var unexpected: Exception? = null
                    val result = try {
                        response.use {
                            if (!it.isSuccessful) {
                                return@use PortraitResult.Unavailable(PortraitResult.Reason.HTTP)
                            }
                            continuation.context.ensureActive()
                            val body = it.body
                            if (body.contentLength() > maxBytes) throw InvalidPortrait()
                            body.byteStream().use { input ->
                                temporary.outputStream().use { output ->
                                    val buffer = ByteArray(8192)
                                    var total = 0L
                                    while (true) {
                                        continuation.context.ensureActive()
                                        val count = input.read(buffer)
                                        if (count == -1) break
                                        total += count
                                        if (total > maxBytes) throw InvalidPortrait()
                                        output.write(buffer, 0, count)
                                    }
                                }
                            }
                            if (!isReadableImage(temporary)) throw InvalidPortrait()
                            continuation.context.ensureActive()
                            if (!temporary.renameTo(saved)) {
                                PortraitResult.Unavailable(PortraitResult.Reason.STORAGE)
                            } else {
                                PortraitResult.Ready(saved)
                            }
                        }
                    } catch (_: InvalidPortrait) {
                        PortraitResult.Unavailable(PortraitResult.Reason.INVALID_IMAGE)
                    } catch (_: IOException) {
                        PortraitResult.Unavailable(PortraitResult.Reason.NETWORK)
                    } catch (failure: Exception) {
                        unexpected = failure
                        null
                    } finally {
                        temporary.delete()
                    }
                    // The caller may inspect files immediately after return. Clean up first.
                    if (continuation.isActive) {
                        val failure = unexpected
                        if (failure != null) continuation.resumeWithException(failure)
                        else continuation.resume(checkNotNull(result))
                    }
                }
            })
        }
}

private fun readableImage(file: File): Boolean {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    return bounds.outWidth > 0 && bounds.outHeight > 0
}
