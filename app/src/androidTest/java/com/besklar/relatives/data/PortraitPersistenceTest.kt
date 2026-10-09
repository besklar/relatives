package com.besklar.relatives.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import com.besklar.relatives.ui.SavedPortraitLoader

@RunWith(AndroidJUnit4::class)
class PortraitPersistenceTest {
    @Test fun fullscreenDecoderCapsLongestSideAndWorksFromSavedFile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.filesDir, "portrait-test-${UUID.randomUUID()}")
        val server = MockWebServer()
        val client = OkHttpClient()
        server.start()
        try {
            val bytes = ByteArrayOutputStream().use { output ->
                val bitmap = Bitmap.createBitmap(4096, 128, Bitmap.Config.ARGB_8888)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                bitmap.recycle()
                output.toByteArray()
            }
            server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
            val store = PortraitStore(directory, client, server.url("/"))
            val loader = SavedPortraitLoader(store)
            val capped = loader.loadFullSize("wide.png", 512)!!
            assertEquals(512, capped.width)
            assertEquals(16, capped.height)
            assertSame(capped, loader.loadFullSize("wide.png", 512))
            server.shutdown()
            val offline = SavedPortraitLoader(store).loadFullSize("wide.png", 2048)!!
            assertEquals(2048, offline.width)
            assertEquals(64, offline.height)
        } finally {
            server.shutdown()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test fun displayDecoderSamplesLargeImageAndRetainsOriginalFile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.filesDir, "portrait-test-${UUID.randomUUID()}")
        val server = MockWebServer()
        val client = OkHttpClient()
        server.start()
        try {
            val bytes = ByteArrayOutputStream().use { output ->
                val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                bitmap.recycle()
                output.toByteArray()
            }
            server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
            val store = PortraitStore(directory, client, server.url("/"))
            val loader = SavedPortraitLoader(store)
            val displayed = loader.load("large.png", 64)!!
            assertEquals(64, displayed.width)
            assertSame(displayed, loader.peek("large.png"))
            assertSame(displayed, loader.load("large.png", 64))
            val larger = loader.load("large.png", 256)!!
            assertEquals(256, larger.width)
            assertSame(larger, loader.peek("large.png"))
            // The outgoing composition may still hold the smaller decode.
            assertFalse(displayed.isRecycled)
            val original = BitmapFactory.decodeFile((store.load("large.png") as PortraitResult.Ready).file.path)
            assertEquals(512, original.width)
            original.recycle()
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
            directory.deleteRecursively()
        }
    }
    @Test fun memoryCacheEvictsOldImagesWithoutRecyclingActiveReferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.filesDir, "portrait-test-${UUID.randomUUID()}")
        val server = MockWebServer()
        val client = OkHttpClient()
        server.start()
        try {
            val bytes = ByteArrayOutputStream().use { output ->
                val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                bitmap.recycle()
                output.toByteArray()
            }
            val loader = SavedPortraitLoader(PortraitStore(directory, client, server.url("/")))
            var first: Bitmap? = null
            repeat(9) { index ->
                server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
                val decoded = loader.load("$index.png", 512)!!
                if (index == 0) first = decoded
            }
            assertNull(loader.peek("0.png"))
            assertNotNull(loader.peek("8.png"))
            assertFalse(first!!.isRecycled)
            assertNull(loader.load("", 64))
            assertEquals(9, server.requestCount)
        } finally {
            server.shutdown()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test fun realImageIsReadableAfterStoreRecreationWithoutNetwork() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.filesDir, "portrait-test-${UUID.randomUUID()}")
        val server = MockWebServer()
        val client = OkHttpClient()
        server.start()
        val base = server.url("/")
        try {
            val bytes = ByteArrayOutputStream().use { output ->
                val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.BLUE)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                bitmap.recycle()
                output.toByteArray()
            }
            server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
            val first = PortraitStore(directory, client, base).load("portraits/A-1.png")
            assertTrue(first is PortraitResult.Ready)
            val file = (first as PortraitResult.Ready).file
            server.shutdown()
            val second = PortraitStore(directory, client, base).load("portraits/A-1.png")
            assertEquals(first, second)
            val decoded = BitmapFactory.decodeFile(file.path)
            assertNotNull(decoded)
            assertEquals(8, decoded.width)
            decoded.recycle()
        } finally {
            server.shutdown()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test fun invalidImagePublishesNoFile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.filesDir, "portrait-test-${UUID.randomUUID()}")
        val server = MockWebServer()
        val client = OkHttpClient()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("not an image"))
            assertEquals(PortraitResult.Unavailable(PortraitResult.Reason.INVALID_IMAGE),
                PortraitStore(directory, client, server.url("/")).load("bad.png"))
            assertTrue(directory.listFiles()!!.isEmpty())
        } finally {
            server.shutdown()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
            directory.deleteRecursively()
        }
    }
}
