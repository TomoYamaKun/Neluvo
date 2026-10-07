//app/src/main/java/co/neluvo/papa/PieChartView.kt
//ver 1.00-22
package co.neluvo.papa

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.jvm.JvmOverloads

class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val rectF = RectF()
    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    // 静寂, 小音, 中音, 大音 の割合(%)
    private var quietPct = 0f
    private var lowPct = 0f
    private var midPct = 0f
    private var highPct = 0f

    private val colors = intArrayOf(
        Color.parseColor("#4CAF50"), // 静寂: 緑
        Color.parseColor("#2196F3"), // 小: 青
        Color.parseColor("#FF9800"), // 中: オレンジ
        Color.parseColor("#F44336")  // 大: 赤
    )

    fun setData(quiet: Int, low: Int, mid: Int, high: Int) {
        val total = (quiet + low + mid + high).coerceAtLeast(1).toFloat()
        quietPct = quiet / total
        lowPct = low / total
        midPct = mid / total
        highPct = high / total
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = Math.min(width, height).toFloat()
        if (size <= 0) return

        val padding = 16f
        val radius = (size - padding * 2) / 2f
        val cx = width / 2f
        val cy = height / 2f

        rectF.set(cx - radius, cy - radius, cx + radius, cy + radius)

        var startAngle = -90f
        val pcts = floatArrayOf(quietPct, lowPct, midPct, highPct)

        for (i in pcts.indices) {
            val sweepAngle = pcts[i] * 360f
            if (sweepAngle > 0) {
                paint.color = colors[i]
                canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
                startAngle += sweepAngle
            }
        }

        // ドーナツ穴
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius * 0.5f, paint)
    }
}
