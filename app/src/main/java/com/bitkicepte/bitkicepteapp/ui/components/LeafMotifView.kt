package com.bitkicepte.bitkicepteapp.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

data class LeafParticle(
    var x: Float,
    var y: Float,
    var size: Float,
    var alpha: Float,
    val speed: Float
)

class LeafMotifView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particles = mutableListOf<LeafParticle>()
    private val random = Random(System.currentTimeMillis())

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { animation ->
            updateParticles(animation.animatedValue as Float)
            invalidate()
        }
    }

    init {
        leafPaint.color = Color.WHITE
        leafPaint.style = Paint.Style.FILL

        glowPaint.color = Color.argb(100, 255, 255, 255)
        glowPaint.maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        particles.clear()
        for (i in 0..5) {
            particles.add(
                LeafParticle(
                    x = random.nextFloat() * w,
                    y = random.nextFloat() * h,
                    size = random.nextFloat() * 40 + 20,
                    alpha = random.nextFloat() * 0.4f + 0.1f,
                    speed = random.nextFloat() * 0.5f + 0.2f
                )
            )
        }
    }

    private fun updateParticles(progress: Float) {
        particles.forEach { particle ->
            particle.y += particle.speed * 2
            particle.alpha = (0.3f * (1 - progress) + 0.1f).coerceIn(0.1f, 0.4f)

            if (particle.y > height) {
                particle.y = -particle.size
                particle.x = random.nextFloat() * width
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        particles.forEach { particle ->
            leafPaint.alpha = (particle.alpha * 255).toInt().coerceIn(0, 255)
            glowPaint.alpha = (particle.alpha * 100).toInt().coerceIn(0, 100)

            // Draw glow
            canvas.drawOval(
                particle.x - particle.size,
                particle.y - particle.size,
                particle.x + particle.size,
                particle.y + particle.size,
                glowPaint
            )

            // Draw leaf oval
            canvas.drawOval(
                particle.x - particle.size * 0.7f,
                particle.y - particle.size * 0.5f,
                particle.x + particle.size * 0.7f,
                particle.y + particle.size * 0.5f,
                leafPaint
            )
        }
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
