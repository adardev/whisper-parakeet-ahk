package com.nemotron.voiceime.health

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ExpiringValueCacheTest {
    @Test fun expiresAndInvalidatesWhenCredentialsChange() {
        var now = 100L
        val cache = ExpiringValueCache<String> { now }
        var loads = 0
        fun read(key: String) = cache.getOrLoad(key) { "token-${++loads}" to 1000L }
        assertEquals("token-1", read("account-a"))
        now = 1099L
        assertEquals("token-1", read("account-a"))
        now = 1100L
        assertEquals("token-2", read("account-a"))
        assertEquals("token-3", read("account-b"))
        cache.invalidate()
        assertEquals("token-4", read("account-b"))
    }

    @Test fun concurrentJobsPerformOnlyOneCredentialRefresh() {
        val cache = ExpiringValueCache<String> { 0L }
        val loads = AtomicInteger()
        val executor = Executors.newFixedThreadPool(8)
        try {
            val tasks = (0 until 32).map { Callable {
                cache.getOrLoad("same-account") { loads.incrementAndGet(); "valid" to 1000L }
            } }
            executor.invokeAll(tasks).forEach { assertEquals("valid", it.get()) }
            assertEquals(1, loads.get())
        } finally { executor.shutdownNow() }
    }

    @Test fun failedOrUnknownLifetimeResponsesAreNotCached() {
        val cache = ExpiringValueCache<String> { 0L }
        var loads = 0
        repeat(2) {
            assertEquals("uncached", cache.getOrLoad("same-account") { loads++; "uncached" to 0L })
        }
        assertEquals(2, loads)
        try { cache.getOrLoad("same-account") { throw IllegalStateException("network") }; fail() }
        catch (_: IllegalStateException) { }
        assertEquals("recovered", cache.getOrLoad("same-account") { "recovered" to 1000L })
    }
}
