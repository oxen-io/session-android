package org.thoughtcrime.securesms.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ReplacementSpan

/**
 * A Span that draws text with a rounded background.
 *
 * @param textColor - The color of the text.
 * @param backgroundColor - The color of the background.
 * @param cornerRadius - The corner radius of the background in pixels. Defaults to 8dp.
 * @param paddingHorizontal - The horizontal padding of the text in pixels. Defaults to 3dp.
 * @param paddingVertical - The vertical padding of the text in pixels. Defaults to 3dp.
 */


class RoundedBackgroundSpan(
    context: Context,
    private val textColor: Int,
    private val backgroundColor: Int,
    private val cornerRadius: Float = toPx(8, context.resources).toFloat(), // setting some Session defaults
    private val paddingHorizontal: Float = toPx(3, context.resources).toFloat(),
    private val paddingVertical: Float = toPx(3, context.resources).toFloat()
) : ReplacementSpan() {

    override fun draw(
        canvas: Canvas, text: CharSequence, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint
    ) {
        // the top needs to take into account the font and the required vertical padding
        val newTop = y + paint.fontMetrics.ascent - paddingVertical
        val newBottom = y + paint.fontMetrics.descent + paddingVertical
        val rect = RectF(
            x,
            newTop,
            x + measureText(paint, text, start, end) + 2 * paddingHorizontal,
            newBottom
        )
        paint.color = backgroundColor

        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        paint.color = textColor
        canvas.drawText(text, start, end, x + paddingHorizontal, y.toFloat(), paint)
    }

    override fun getSize(
        paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?
    ): Int {
        // If the span covers the whole text, and the height is not set, draw() will not be called for the span.
        // To help with that we need to take the font metric into account
        val metrics = paint.fontMetricsInt
        if (fm != null) {
            fm.top = metrics.top
            fm.ascent = metrics.ascent
            fm.descent = metrics.descent

            fm.bottom = metrics.bottom
        }

        return (paint.measureText(text, start, end) + 2 * paddingHorizontal).toInt()
    }

    private fun measureText(
        paint: Paint, text: CharSequence, start: Int, end: Int
    ): Float {
        return paint.measureText(text, start, end)
    }
}
