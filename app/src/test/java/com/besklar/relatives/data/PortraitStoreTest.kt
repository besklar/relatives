package com.besklar.relatives.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class PortraitStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val server = MockWebServer()
    private val client = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()
    private lateinit var directory: File

    @Before fun start() {
        server.start()
        directory = temporary.newFolder("portraits")
    }
    @After fun stop() {
        server.shutdown()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    private fun store(maxBytes: Long = 8_000_000) = PortraitStore(directory, client, server.url("/"),
        isReadableImage = { it.readText().startsWith("image") }, maxBytes = maxBytes)

    @Test fun savedPortraitSurvivesStoreRecreationAndUnavailableNetwork() = runTest {
        server.enqueue(MockResponse().setBody("image: saved bytes"))
        val saved = store().load("portraits/A-1.jpg") as PortraitResult.Ready
        assertEquals("/portraits/A-1.jpg", server.takeRequest().path)
        server.shutdown()
        val reloaded = store().load("portraits/A-1.jpg") as PortraitResult.Ready
        assertEquals(saved.file, reloaded.file)
        assertEquals("image: saved bytes", reloaded.file.readText())
        assertEquals(1, directory.listFiles()!!.size)
    }

    @Test fun concurrentLoadsDownloadOnce() = runTest {
        server.enqueue(MockResponse().setBody("image"))
        val store = store()
        val first = async(Dispatchers.IO) { store.load("portraits/A-1.jpg") }
        val second = async(Dispatchers.IO) { store.load("portraits/A-1.jpg") }
        assertEquals(first.await(), second.await())
        assertEquals(1, server.requestCount)
    }

    @Test fun failedAndInvalidDownloadsPublishNoFilesAndCanBeRetried() = runTest {
        val store = store()
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(PortraitResult.Unavailable(PortraitResult.Reason.HTTP), store.load("missing.jpg"))
        server.enqueue(MockResponse().setBody("not an image"))
        assertEquals(PortraitResult.Unavailable(PortraitResult.Reason.INVALID_IMAGE), store.load("bad.jpg"))
        assertTrue(directory.listFiles()!!.isEmpty())
        server.enqueue(MockResponse().setBody("image"))
        assertTrue(store.load("bad.jpg") is PortraitResult.Ready)
    }

    @Test fun oversizedFixedAndChunkedResponsesAreRejected() = runTest {
        server.enqueue(MockResponse().setBody("image too large"))
        assertEquals(PortraitResult.Unavailable(PortraitResult.Reason.INVALID_IMAGE), store(5).load("large.jpg"))
        server.enqueue(MockResponse().setChunkedBody("image too large", 2))
        assertEquals(PortraitResult.Unavailable(PortraitResult.Reason.INVALID_IMAGE), store(5).load("chunked.jpg"))
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun cancellationClosesRequestAndRemovesIncompleteFile() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = async(Dispatchers.IO) { store().load("slow.jpg") }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun disconnectedResponseLeavesNoPartialFileOrDamageToSavedImage() = runTest {
        val store = store()
        server.enqueue(MockResponse().setBody("image good"))
        val good = store.load("good.jpg") as PortraitResult.Ready
        server.enqueue(MockResponse().setBody("image".repeat(5000)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        assertTrue(store.load("broken.jpg") is PortraitResult.Unavailable)
        assertEquals(listOf(good.file), directory.listFiles()!!.toList())
        assertEquals("image good", good.file.readText())
    }

    @Test fun externalAndBlankPathsAreRejectedBeforeRequest() = runTest {
        for (path in listOf("", "https://other.example/image.jpg", "//other.example/image.jpg")) {
            assertEquals(PortraitResult.Unavailable(PortraitResult.Reason.INVALID_PATH), store().load(path))
        }
        assertEquals(0, server.requestCount)
    }
}
