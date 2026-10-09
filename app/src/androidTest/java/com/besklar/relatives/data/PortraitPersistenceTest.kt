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

@RunWith(AndroidJUnit4::class)
class PortraitPersistenceTest {
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
