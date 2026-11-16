package com.nemotron.voiceime.health

/** Single-entry, single-flight cache. Its clock must be monotonic. */
internal class ExpiringValueCache<T>(private val clockMillis: () -> Long) {
    private data class Entry<T>(val key: String, val value: T, val until: Long)
    private var entry: Entry<T>? = null

    @Synchronized fun getOrLoad(key: String, load: () -> Pair<T, Long>): T {
        val now = clockMillis()
        entry?.takeIf { it.key == key && now < it.until }?.let { return it.value }
        // Run the load under the same lock so concurrent tile/jobs share it.
        val (value, lifetimeMillis) = load()
        entry = if (lifetimeMillis > 0) Entry(key, value, now + lifetimeMillis) else null
        return value
    }

    @Synchronized fun invalidate() { entry = null }
}
