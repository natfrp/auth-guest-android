package com.natfrp.authguest

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.min

/** Shared by the overlay and the analyzer's ViewPort crop; a centered square survives rotation. */
internal fun qrScanRegion(bounds: Rect): Rect {
    val side = (min(bounds.width(), bounds.height()) * 0.75f).toInt()
    val left = bounds.left + (bounds.width() - side) / 2
    val top = bounds.top + (bounds.height() - side) / 2
    return Rect(left, top, left + side, top + side)
}

class QrScanOverlay @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val region = Rect()
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics)
    }
    private val hint = context.getString(R.string.qr_scan_hint)

    init {
        contentDescription = hint
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        region.set(qrScanRegion(Rect(0, 0, w, h)))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val saved = canvas.save()
        canvas.clipOutRect(region)
        canvas.drawColor(0x99000000.toInt())
        canvas.restoreToCount(saved)
        canvas.drawRect(region, framePaint)
        val baseline = min(region.bottom + 32f * density, height - 16f * density)
        canvas.drawText(hint, width / 2f, baseline, textPaint)
    }
}
