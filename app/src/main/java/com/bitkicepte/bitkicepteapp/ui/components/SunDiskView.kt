package com.bitkicepte.bitkicepteapp.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class SunDiskView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var rotation = 0f
    private var glowAlpha = 255
    private var glowDirection = -10

    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 4000
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { animation ->
            rotation = animation.animatedValue as Float
            glowAlpha += glowDirection
            if (glowAlpha >= 255) glowDirection = -10
            if (glowAlpha <= 100) glowDirection = 10
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val radius = (w * 0.35f).coerceAtMost((h * 0.35f))

        val gradient = RadialGradient(
            w / 2f, h / 2f, radius,
            intArrayOf(0xFFFFD54F.toInt(), 0xFFFFA726.toInt()),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        sunPaint.shader = gradient

        rayPaint.color = 0xFFFFB300.toInt()
        rayPaint.strokeWidth = 3f

        glowPaint.color = Color.argb(glowAlpha, 255, 179, 0)
        glowPaint.maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (width * 0.35f).coerceAtMost((height * 0.35f))
        val rayRadius = radius + 15f

        canvas.save()
        canvas.rotate(rotation, centerX, centerY)

        // Draw rays
        for (i in 0..11) {
            val angle = (i * 30) * Math.PI / 180
            val startX = centerX + radius * cos(angle).toFloat()
            val startY = centerY + radius * sin(angle).toFloat()
            val endX = centerX + rayRadius * cos(angle).toFloat()
            val endY = centerY + rayRadius * sin(angle).toFloat()
            canvas.drawLine(startX, startY, endX, endY, rayPaint)
        }

        canvas.restore()

        // Draw glow
        glowPaint.alpha = glowAlpha
        canvas.drawCircle(centerX, centerY, radius + 8, glowPaint)

        // Draw sun disk
        canvas.drawCircle(centerX, centerY, radius, sunPaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }
}
