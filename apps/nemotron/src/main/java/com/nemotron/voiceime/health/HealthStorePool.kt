package com.nemotron.voiceime.health

import android.content.Context
import com.samsung.android.sdk.health.data.HealthDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** A process owns one SDK store/executor set; individual requests stay cancellable. */
internal class HealthStorePool(
    private val create: (Context, CoroutineScope) -> HealthDataStore
) {
    private val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var store: HealthDataStore? = null

    fun get(context: Context): HealthDataStore {
        store?.let { return it }
        return synchronized(this) {
            store ?: create(context.applicationContext, sdkScope).also { store = it }
        }
    }
}
