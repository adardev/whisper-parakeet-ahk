package com.nemotron.voiceime.record

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.util.Log
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/** Tootsie's nine voice bars, drawn inside the pressed Samsung space key. */
object KeyboardDictationFeedback {
    private val main = Handler(Looper.getMainLooper())
    private var keyboard = WeakReference<View>(null)
    private var indicator: VoiceWaveform? = null
    private var tapX = 0f
    private var labels = emptyList<LabelSlot>()
    private val languageLabel = Regex("""^[\p{L}][\p{L} .-]*\s*\([\p{L}-]+\)$""")

    fun bind(view: View, x: Float) {
        clear()
        keyboard = WeakReference(view)
        tapX = x
    }

    fun setRecording(recording: Boolean) {
        main.post {
            clear()
            val view = keyboard.get() ?: return@post
            if (!recording || !view.isAttachedToWindow) return@post
            val density = view.resources.displayMetrics.density
            val origin = IntArray(2).also { view.getLocationOnScreen(it) }
            val spaces = mutableListOf<Pair<TextView, Rect>>()
            findSpaceLabels(view, origin, view.height, spaces)
            spaces.sortBy { abs(it.second.exactCenterX() - tapX) }
            Log.d("KeyboardDictationFeedback", "Space labels: ${spaces.size}")
            if (spaces.isEmpty()) return@post
            labels = spaces.map { (label, _) -> LabelSlot(WeakReference(label), label.alpha) }
            spaces.forEach { (label, _) -> label.alpha = 0f }
            val areas = spaces.map { it.second }
            val region = Rect(areas.first()).also { union -> areas.drop(1).forEach { union.union(it) } }
            val mark = VoiceWaveform(density, areas).apply { bounds = region }
            indicator = mark
            view.overlay.add(mark)
        }
    }

    fun setLevel(rms: Float) {
        main.post { indicator?.setLevel(rms) }
    }

    fun pulse(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(35, 160))
        } catch (_: Throwable) { }
    }

    private fun clear() {
        indicator?.let { keyboard.get()?.overlay?.remove(it) }
        indicator = null
        labels.forEach { slot -> slot.view.get()?.alpha = slot.alpha }
        labels = emptyList()
    }

    private data class LabelSlot(val view: WeakReference<TextView>, val alpha: Float)

    private fun findSpaceLabels(view: View, origin: IntArray, keyboardHeight: Int,
                               result: MutableList<Pair<TextView, Rect>>) {
        if (!view.isShown) return
        if (view is TextView && languageLabel.matches(view.text.toString().trim())) {
            val position = IntArray(2).also { view.getLocationOnScreen(it) }
            val area = Rect(position[0] - origin[0], position[1] - origin[1],
                position[0] - origin[0] + view.width, position[1] - origin[1] + view.height)
            if (area.exactCenterY() > keyboardHeight * .65f && area.width() > area.height()) {
                result.add(view to area)
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findSpaceLabels(view.getChildAt(i), origin, keyboardHeight, result)
        }
    }

    private class VoiceWaveform(private val density: Float, private val areas: List<Rect>) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            strokeWidth = 3 * density
            strokeCap = Paint.Cap.ROUND
        }
        private var level = .15f
        private var phase = 0f

        fun setLevel(rms: Float) {
            level = max(.12f, ((rms + 2) / 14).coerceIn(.12f, 1f))
            phase += .35f
            invalidateSelf()
        }

        override fun draw(canvas: Canvas) {
            for (area in areas) {
                val clusterWidth = minOf(100 * density, area.width() - 16 * density)
                val gap = (clusterWidth - paint.strokeWidth) / 8f
                val start = area.exactCenterX() - clusterWidth / 2 + paint.strokeWidth / 2
                val center = area.exactCenterY()
                val availableHeight = minOf(24 * density, area.height() * .62f)
                for (i in 0 until 9) {
                    val wave = .45f + .55f * abs(sin(phase + i * .8f))
                    val height = (availableHeight * .18f + availableHeight * .62f * level * wave)
                        .coerceAtMost(availableHeight * .88f)
                    val x = start + gap * i
                    canvas.drawLine(x, center - height / 2, x, center + height / 2, paint)
                }
            }
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
        @Deprecated("Deprecated in Android")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
