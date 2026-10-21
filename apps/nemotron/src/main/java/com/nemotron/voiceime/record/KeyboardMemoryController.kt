package com.nemotron.voiceime.record

import android.content.ComponentCallbacks2
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference

/** Release HoneyBoard's disposable caches once after a prolonged keyboard hide. */
object KeyboardMemoryController {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingTrim: Runnable? = null

    @JvmStatic
    fun onHidden(service: InputMethodService) {
        onShown()
        val reference = WeakReference(service)
        val trim = Runnable {
            pendingTrim = null
            val current = reference.get() ?: return@Runnable
            if (!current.isInputViewShown) {
                // Use Samsung's existing cleanup path, including its throttle.
                current.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
            }
        }
        pendingTrim = trim
        handler.postDelayed(trim, 30_000L)
    }

    @JvmStatic
    fun onShown() {
        pendingTrim?.let(handler::removeCallbacks)
        pendingTrim = null
    }
}
