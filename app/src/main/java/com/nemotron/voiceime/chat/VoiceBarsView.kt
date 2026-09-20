package com.nemotron.voiceime.chat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

class VoiceBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8FC1FF.toInt()
        strokeWidth = 3f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    private var level = 0.15f
    private var phase = 0f

    fun setLevel(rms: Float) {
        level = max(0.12f, ((rms + 2f) / 14f).coerceIn(0.12f, 1f))
        phase += 0.35f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val count = 9
        val gap = width / (count + 1f)
        val center = height / 2f
        for (i in 0 until count) {
            val wave = 0.45f + 0.55f * abs(sin(phase + i * 0.8f))
            val h = (height * 0.18f + height * 0.62f * level * wave).coerceAtMost(height * 0.88f)
            val x = gap * (i + 1)
            canvas.drawLine(x, center - h / 2f, x, center + h / 2f, paint)
        }
    }
}
