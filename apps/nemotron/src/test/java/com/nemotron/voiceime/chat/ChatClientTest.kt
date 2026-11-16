package com.nemotron.voiceime.chat

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger

class ChatClientTest {
    private lateinit var server: MockWebServer

    @Before fun startServer() { server = MockWebServer(); server.start() }
    @After fun stopServer() { server.shutdown() }

    private fun client() = ChatClient(server.url("/").toString().trimEnd('/'))

    @Test fun differentScreensReuseOneConnection() {
        repeat(2) {
            server.enqueue(MockResponse().setBody("{\"conversations\":[]}"))
            val done = CountDownLatch(1)
            val error = AtomicReference<Throwable>()
            client().conversations({ done.countDown() }, { error.set(it); done.countDown() })
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertNull(error.get())
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("/api/conversations", request.path)
            assertEquals(it, request.sequenceNumber)
        }
    }

    @Test fun failedSendReportsErrorAndReleasesConnection() {
        val api = client()
        server.enqueue(MockResponse().setResponseCode(503).setBody("{\"error\":\"temporarily unavailable\"}"))
        val done = CountDownLatch(1)
        val error = AtomicReference<Throwable>()
        api.send("hello", "nemotron", emptyList(), { done.countDown() }, { error.set(it); done.countDown() })
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals("temporarily unavailable", error.get()?.message)
        assertEquals(0, server.takeRequest(5, TimeUnit.SECONDS)!!.sequenceNumber)
        server.enqueue(MockResponse().setBody("{\"conversations\":[]}"))
        val recovered = CountDownLatch(1)
        api.conversations({ recovered.countDown() }, { fail(it.toString()) })
        assertTrue(recovered.await(5, TimeUnit.SECONDS))
        assertEquals(1, server.takeRequest(5, TimeUnit.SECONDS)!!.sequenceNumber)
    }

    @Test fun cancellationStopsAnInFlightAnswerWithoutCancellingOtherScreens() {
        val api = client()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val abandonedCallbacks = AtomicInteger()
        api.stream("hello", "nemotron", emptyList(), onToken = { abandonedCallbacks.incrementAndGet() },
            onComplete = { abandonedCallbacks.incrementAndGet() }, onError = { abandonedCallbacks.incrementAndGet() })
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        api.cancel()
        val recovered = CountDownLatch(1)
        val error = AtomicReference<Throwable>()
        server.enqueue(MockResponse().setBody("{\"conversations\":[]}"))
        client().conversations({ recovered.countDown() }, { error.set(it); recovered.countDown() })
        assertTrue(recovered.await(5, TimeUnit.SECONDS))
        assertNull(error.get())
        assertEquals(0, abandonedCallbacks.get())
    }
}
