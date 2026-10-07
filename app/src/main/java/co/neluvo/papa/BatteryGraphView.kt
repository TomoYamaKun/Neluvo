//app/src/main/java/co/neluvo/papa/BatteryGraphView.kt
//ver 1.00-29
package co.neluvo.papa

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class BatteryGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class DataPoint(val level: Int, val isCharging: Boolean)

    private val dataPoints = mutableListOf<DataPoint>()

    private val dischargePaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#2196F3") // 消費中: 青
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val chargePaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#4CAF50") // 充電中: 緑
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val guideLinePaint = Paint().apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 2f
    }

    private val textPaint = Paint().apply {
        color = Color.parseColor("#999999")
        textSize = 24f
        isAntiAlias = true
    }

    fun setData(data: List<DataPoint>) {
        dataPoints.clear()
        dataPoints.addAll(data)
        invalidate()
    }

    fun clear() {
        dataPoints.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val paddingY = 30f
        val graphH = h - paddingY * 2

        // ガイドライン (100%, 50%, 0%)
        canvas.drawLine(0f, paddingY, w, paddingY, guideLinePaint)
        canvas.drawLine(0f, paddingY + graphH / 2, w, paddingY + graphH / 2, guideLinePaint)
        canvas.drawLine(0f, h - paddingY, w, h - paddingY, guideLinePaint)

        canvas.drawText("100%", 4f, paddingY - 4f, textPaint)
        canvas.drawText("50%", 4f, paddingY + graphH / 2 - 4f, textPaint)
        canvas.drawText("0%", 4f, h - paddingY - 4f, textPaint)

        if (dataPoints.isEmpty()) return

        val count = dataPoints.size
        val stepX = w / count.coerceAtLeast(1).toFloat()

        var prevX = 0f
        var prevY = 0f

        for (i in 0 until count) {
            val point = dataPoints[i]
            // バッテリーレベル(0〜100)をY座標に変換
            val level = point.level.coerceIn(0, 100)
            val currX = i * stepX
            val currY = h - paddingY - (graphH * (level / 100f))

            if (i > 0) {
                val paint = if (point.isCharging) chargePaint else dischargePaint
                canvas.drawLine(prevX, prevY, currX, currY, paint)
            }

            prevX = currX
            prevY = currY
        }
    }
}
