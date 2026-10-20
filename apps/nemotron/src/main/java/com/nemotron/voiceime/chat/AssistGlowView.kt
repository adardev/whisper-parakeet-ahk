package com.nemotron.voiceime.chat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator

class AssistGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var pulse = 0f
    private var animator: ValueAnimator? = null

    init { setLayerType(View.LAYER_TYPE_SOFTWARE, null) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                pulse = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = 0f
        val rect = RectF(inset, inset, width - inset, height - inset)
        // Radio de esquinas de la pantalla, no radio de una burbuja de chat.
        paint.strokeWidth = 16f + (pulse * 5f)
        paint.color = Color.argb((30 + pulse * 28).toInt(), 47, 128, 255)
        paint.setShadowLayer(38f + (pulse * 18f), 0f, 0f, Color.argb((95 + pulse * 65).toInt(), 47, 128, 255))
        val radius = 33f * resources.displayMetrics.density
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.clearShadowLayer()
        paint.strokeWidth = 3f + (pulse * 2f)
        paint.color = Color.argb((105 + pulse * 95).toInt(), 91, 166, 255)
        canvas.drawRoundRect(rect, radius, radius, paint)
    }
}
