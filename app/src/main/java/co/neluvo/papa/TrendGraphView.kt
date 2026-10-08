//==================================================
// FILE: /app/src/main/java/co/neluvo/papa/TrendGraphView.kt
// VER : 1.01-11
//==================================================
package co.neluvo.papa

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class TrendGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class TrendData(
        val label: String, 
        val hasData: Boolean, 
        val quietPct: Float, 
        val normalPct: Float, 
        val snorePct: Float, 
        val heavyPct: Float
    )

    private val dataList = mutableListOf<TrendData>()

    private val paintQuiet = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4CAF50") }
    private val paintNormal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#03A9F4") }
    private val paintSnore = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFEB3B") }
    private val paintHeavy = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F44336") }
    private val paintEmpty = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#666666")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    fun setData(list: List<TrendData>) {
        try {
            dataList.clear()
            dataList.addAll(list)
            invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        try {
            if (dataList.isEmpty()) return

            val w = width.toFloat()
            val h = height.toFloat()
            val paddingBottom = 50f
            val paddingTop = 20f
            val graphH = h - paddingBottom - paddingTop

            val stepX = w / dataList.size
            val barW = stepX * 0.7f

            for (i in dataList.indices) {
                val data = dataList[i]
                val cx = i * stepX + stepX / 2f
                val left = cx - barW / 2f
                val right = cx + barW / 2f

                if (data.hasData) {
                    var currentBottom = paddingTop + graphH
                    
                    val qH = graphH * (data.quietPct / 100f)
                    canvas.drawRect(left, currentBottom - qH, right, currentBottom, paintQuiet)
                    currentBottom -= qH

                    val nH = graphH * (data.normalPct / 100f)
                    canvas.drawRect(left, currentBottom - nH, right, currentBottom, paintNormal)
                    currentBottom -= nH

                    val sH = graphH * (data.snorePct / 100f)
                    canvas.drawRect(left, currentBottom - sH, right, currentBottom, paintSnore)
                    currentBottom -= sH

                    val hH = graphH * (data.heavyPct / 100f)
                    canvas.drawRect(left, currentBottom - hH, right, currentBottom, paintHeavy)
                } else {
                    // 未実行日はグレーの点線枠（ストローク）で「空き」を表現
                    canvas.drawRect(left, paddingTop, right, paddingTop + graphH, paintEmpty)
                }

                canvas.drawText(data.label, cx, h - 10f, textPaint)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
