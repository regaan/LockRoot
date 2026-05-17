package com.regaan.lockroot.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import com.regaan.lockroot.R

@SuppressLint("DrawAllocation", "ViewConstructor")
class SecurityIllustrationView(
    context: Context,
    private val mode: Mode,
) : View(context) {
    private val primaryColor = context.getColor(R.color.app_primary)
    private val primaryDarkColor = context.getColor(R.color.app_primary_dark)
    private val softColor = context.getColor(R.color.app_primary_soft)
    private val accentColor = context.getColor(R.color.app_accent_green)
    private val whiteColor = context.getColor(R.color.app_white)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = primaryColor
    }
    private val darkFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = primaryDarkColor
    }
    private val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = softColor
    }
    private val accentFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = accentColor
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        color = primaryDarkColor
    }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = whiteColor
    }
    private val lightGray = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFE8EDE9.toInt()
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x15000000
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        drawDecorDots(canvas, w, h)
        canvas.drawOval(RectF(w * 0.15f, h * 0.18f, w * 0.85f, h * 0.85f), soft)

        when (mode) {
            Mode.SAFE -> drawSafe(canvas, w, h)
            Mode.LOCK -> drawLock(canvas, w, h)
            Mode.EMPTY -> drawEmpty(canvas, w, h)
        }
    }

    private fun drawDecorDots(canvas: Canvas, w: Float, h: Float) {
        val positions = listOf(
            0.12f to 0.3f, 0.88f to 0.35f, 0.15f to 0.7f,
            0.85f to 0.72f, 0.5f to 0.12f, 0.35f to 0.88f
        )
        positions.forEach { (x, y) ->
            canvas.drawCircle(w * x, h * y, w * 0.012f, dotPaint)
        }
    }

    private fun drawSafe(canvas: Canvas, w: Float, h: Float) {
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x18000000
        }
        canvas.drawRoundRect(
            RectF(w * 0.26f, h * 0.38f, w * 0.71f, h * 0.73f),
            20f, 20f, shadowPaint
        )

        val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                w * 0.25f, h * 0.34f, w * 0.7f, h * 0.7f,
                primaryColor, primaryDarkColor, Shader.TileMode.CLAMP
            )
        }
        val box = RectF(w * 0.24f, h * 0.34f, w * 0.69f, h * 0.70f)
        canvas.drawRoundRect(box, 22f, 22f, gradPaint)

        val innerBox = RectF(w * 0.28f, h * 0.38f, w * 0.65f, h * 0.66f)
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x18FFFFFF
        }
        canvas.drawRoundRect(innerBox, 14f, 14f, innerPaint)

        canvas.drawCircle(w * 0.465f, h * 0.52f, w * 0.085f, white)
        val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = primaryDarkColor
        }
        canvas.drawCircle(w * 0.465f, h * 0.52f, w * 0.04f, knobPaint)

        val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
            color = 0xCCFFFFFF.toInt()
        }
        canvas.drawLine(w * 0.635f, h * 0.42f, w * 0.635f, h * 0.62f, handlePaint)

        val legPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = primaryDarkColor
        }
        canvas.drawRoundRect(RectF(w * 0.28f, h * 0.69f, w * 0.34f, h * 0.74f), 4f, 4f, legPaint)
        canvas.drawRoundRect(RectF(w * 0.59f, h * 0.69f, w * 0.65f, h * 0.74f), 4f, 4f, legPaint)

        drawShield(canvas, w * 0.68f, h * 0.24f, w * 0.14f)
    }

    private fun drawLock(canvas: Canvas, w: Float, h: Float) {
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x15000000
        }
        canvas.drawRoundRect(
            RectF(w * 0.33f, h * 0.49f, w * 0.67f, h * 0.76f),
            20f, 20f, shadowPaint
        )

        val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                w * 0.32f, h * 0.46f, w * 0.68f, h * 0.74f,
                primaryColor, primaryDarkColor, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(
            RectF(w * 0.32f, h * 0.46f, w * 0.68f, h * 0.74f),
            20f, 20f, gradPaint
        )

        val archPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
            color = primaryDarkColor
        }
        canvas.drawArc(
            RectF(w * 0.37f, h * 0.24f, w * 0.63f, h * 0.56f),
            200f, 140f, false, archPaint
        )

        canvas.drawCircle(w * 0.5f, h * 0.59f, w * 0.04f, white)

        val keyholePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = primaryDarkColor
        }
        canvas.drawCircle(w * 0.5f, h * 0.59f, w * 0.02f, keyholePaint)
        canvas.drawRoundRect(
            RectF(w * 0.49f, h * 0.605f, w * 0.51f, h * 0.67f),
            3f, 3f, white
        )
    }

    private fun drawEmpty(canvas: Canvas, w: Float, h: Float) {
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x15000000
        }
        canvas.drawRoundRect(
            RectF(w * 0.28f, h * 0.40f, w * 0.72f, h * 0.71f),
            18f, 18f, shadowPaint
        )

        val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                w * 0.27f, h * 0.37f, w * 0.73f, h * 0.69f,
                primaryColor, primaryDarkColor, Shader.TileMode.CLAMP
            )
        }
        val box = RectF(w * 0.27f, h * 0.37f, w * 0.73f, h * 0.69f)
        canvas.drawRoundRect(box, 18f, 18f, gradPaint)

        canvas.drawRoundRect(
            RectF(w * 0.35f, h * 0.45f, w * 0.65f, h * 0.61f),
            10f, 10f, white
        )
        val dotCenter = w * 0.46f
        val dotY = h * 0.53f
        canvas.drawCircle(dotCenter, dotY, w * 0.028f, accentFill)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
            color = primaryDarkColor
        }
        canvas.drawLine(w * 0.52f, dotY, w * 0.62f, dotY, linePaint)

        canvas.drawRoundRect(RectF(w * 0.33f, h * 0.68f, w * 0.39f, h * 0.73f), 4f, 4f, darkFill)
        canvas.drawRoundRect(RectF(w * 0.61f, h * 0.68f, w * 0.67f, h * 0.73f), 4f, 4f, darkFill)
    }

    private fun drawShield(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val path = Path().apply {
            moveTo(cx, cy - size)
            lineTo(cx + size * 0.78f, cy - size * 0.55f)
            lineTo(cx + size * 0.65f, cy + size * 0.55f)
            lineTo(cx, cy + size)
            lineTo(cx - size * 0.65f, cy + size * 0.55f)
            lineTo(cx - size * 0.78f, cy - size * 0.55f)
            close()
        }
        val shieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                cx, cy - size, cx, cy + size,
                accentColor, primaryColor, Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(path, shieldPaint)

        val ws = whiteStroke()
        canvas.drawLine(
            cx - size * 0.25f, cy + size * 0.02f,
            cx - size * 0.05f, cy + size * 0.22f, ws
        )
        canvas.drawLine(
            cx - size * 0.05f, cy + size * 0.22f,
            cx + size * 0.3f, cy - size * 0.22f, ws
        )
    }

    private fun whiteStroke(): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            color = whiteColor
        }

    enum class Mode {
        SAFE,
        LOCK,
        EMPTY,
    }
}
