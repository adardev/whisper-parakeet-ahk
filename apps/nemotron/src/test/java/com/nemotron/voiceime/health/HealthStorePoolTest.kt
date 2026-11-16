package com.nemotron.voiceime.health

import android.content.Context
import com.samsung.android.sdk.health.data.HealthDataStore
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class HealthStorePoolTest {
    @Test fun concurrentExportsShareOneStoreAndUseApplicationContext() {
        val app = mock(Context::class.java)
        val activity = mock(Context::class.java)
        `when`(activity.applicationContext).thenReturn(app)
        val sdk = mock(HealthDataStore::class.java)
        val creates = AtomicInteger()
        val pool = HealthStorePool { context, _ ->
            assertSame(app, context); creates.incrementAndGet(); sdk
        }
        val workers = Executors.newFixedThreadPool(8)
        try {
            val tasks = (0 until 32).map { Callable { pool.get(activity) } }
            workers.invokeAll(tasks).forEach { assertSame(sdk, it.get()) }
            assertEquals(1, creates.get())
        } finally { workers.shutdownNow() }
    }

    @Test fun transientCreationFailureCanRecoverOnTheNextExport() {
        val app = mock(Context::class.java)
        `when`(app.applicationContext).thenReturn(app)
        val sdk = mock(HealthDataStore::class.java)
        var creates = 0
        val pool = HealthStorePool { _, _ ->
            if (++creates == 1) throw IllegalStateException("SDK unavailable")
            sdk
        }
        try { pool.get(app); fail() } catch (_: IllegalStateException) { }
        assertSame(sdk, pool.get(app))
        assertSame(sdk, pool.get(app))
        assertEquals(2, creates)
    }
}
