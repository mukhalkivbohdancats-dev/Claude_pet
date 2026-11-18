package com.bohdan.claudepet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class PixelPetView(context: Context) : View(context) {

    private var bitmap: Bitmap? = null
    private var phase = 0f
    private var direction = 1f
    private var falling = false

    private val imagePaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        isDither = false
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55000000
        style = Paint.Style.FILL
    }

    fun setImage(value: Bitmap) {
        bitmap = value
        invalidate()
    }

    fun setMotion(newPhase: Float, newDirection: Float, isFalling: Boolean) {
        phase = newPhase
        direction = newDirection
        falling = isFalling
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val source = bitmap ?: return
        val centerX = width / 2f
        val centerY = height / 2f
        val availableWidth = width * 0.86f
        val availableHeight = height * 0.86f
        val fit = min(
            availableWidth / max(1, source.width),
            availableHeight / max(1, source.height)
        )
        val drawWidth = source.width * fit
        val drawHeight = source.height * fit
        val bob = if (falling) 0f else abs(sin(phase * 2f)) * dp(1.8f)
        val left = centerX - drawWidth / 2f
        val top = centerY - drawHeight / 2f + bob

        canvas.drawOval(
            RectF(
                centerX - dp(19f),
                height - dp(10f),
                centerX + dp(19f),
                height - dp(5f)
            ),
            shadowPaint
        )

        canvas.save()
        canvas.scale(direction, 1f, centerX, centerY)
        if (!falling) {
            canvas.rotate(sin(phase).toFloat() * 2.0f, centerX, centerY)
        }
        val destination = RectF(left, top, left + drawWidth, top + drawHeight)
        canvas.drawBitmap(source, null, destination, imagePaint)
        canvas.restore()
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density
}
