package com.nemotron.voiceime.net

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class NemotronStreamClientTest {
    private lateinit var server: MockWebServer
    @Before fun startServer() { server = MockWebServer(); server.start() }
    @After fun stopServer() { server.shutdown() }

    @Test fun replacingAnUnresponsiveStreamCompletesOnlyTheNewRequest() {
        val api = NemotronStreamClient("test-key", server.url("/v1").toString().trimEnd('/'))
        val staleCallbacks = AtomicInteger()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        api.stream("old", "test-model", "", { staleCallbacks.incrementAndGet() },
            { staleCallbacks.incrementAndGet() }, { staleCallbacks.incrementAndGet() })
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"new answer\"}}]}\n\ndata: [DONE]\n\n"))
        val done = CountDownLatch(1)
        val answer = AtomicReference<String>()
        val error = AtomicReference<Throwable>()
        val completions = AtomicInteger()
        api.stream("new", "test-model", "", {}, {
            answer.set(it); completions.incrementAndGet(); done.countDown()
        }, { error.set(it); done.countDown() })
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertNull(error.get())
        assertEquals("new answer", answer.get())
        assertEquals(1, completions.get())
        assertEquals(0, staleCallbacks.get())
        api.cancel()
    }
}
