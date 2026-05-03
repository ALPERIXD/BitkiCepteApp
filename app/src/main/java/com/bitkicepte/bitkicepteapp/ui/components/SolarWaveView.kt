package com.bitkicepte.bitkicepteapp.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class SolarWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePath = Path()
    private var waveOffset = 0f
    private var waveAmplitude = 0f
    private var waveFrequency = 0f

    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 3000
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { animation ->
            waveOffset = animation.animatedValue as Float
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        waveAmplitude = h * 0.1f
        waveFrequency = (2 * Math.PI / w).toFloat()

        val gradient = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFFFFB300.toInt(), 0xFFFFF8E1.toInt()),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        wavePaint.shader = gradient
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height * 0.7f

        wavePath.reset()
        wavePath.moveTo(0f, centerY)

        for (x in 0..width.toInt()) {
            val y = centerY + waveAmplitude * sin(
                (x + waveOffset) * waveFrequency
            ).toFloat()
            wavePath.lineTo(x.toFloat(), y)
        }

        wavePath.lineTo(width, height)
        wavePath.lineTo(0f, height)
        wavePath.close()

        canvas.drawPath(wavePath, wavePaint)
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
