//app/src/main/java/co/neluvo/papa/WaveformView.kt
//ver 1.00-23
package co.neluvo.papa

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val amplitudes = mutableListOf<Int>()
    private var maxBars = 60
    private var playbackProgress = -1f

    private val barPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#2196F3")
        style = Paint.Style.FILL
    }

    private val playedBarPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#FF9800")
        style = Paint.Style.FILL
    }

    private val baselinePaint = Paint().apply {
        color = Color.parseColor("#CCCCCC")
        strokeWidth = 2f
    }

    private val rectF = RectF()

    fun addAmplitude(amp: Int) {
        amplitudes.add(amp)
        if (amplitudes.size > maxBars) {
            amplitudes.removeAt(0)
        }
        postInvalidateOnAnimation()
    }

    fun setWaveData(data: List<Int>) {
        amplitudes.clear()
        amplitudes.addAll(data)
        playbackProgress = -1f
        postInvalidateOnAnimation()
    }

    fun setPlaybackProgress(progress: Float) {
        playbackProgress = progress
        postInvalidateOnAnimation()
    }

    fun clear() {
        amplitudes.clear()
        playbackProgress = -1f
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        maxBars = max(20, w / 9)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerY = height / 2f
        canvas.drawLine(0f, centerY, width.toFloat(), centerY, baselinePaint)

        if (amplitudes.isEmpty()) return

        val count = amplitudes.size
        val gap = 3f
        val barWidth = max(2f, (width.toFloat() - (gap * (count + 1))) / count)
        val maxAmp = 25000f

        for (i in 0 until count) {
            val amp = amplitudes[i].toFloat()
            val normalized = min(1.0f, max(0.05f, amp / maxAmp))
            val barHeight = max(4f, (height * 0.8f) * normalized)

            val left = gap + i * (barWidth + gap)
            val top = centerY - (barHeight / 2f)
            val right = left + barWidth
            val bottom = centerY + (barHeight / 2f)

            rectF.set(left, top, right, bottom)

            val paint = if (playbackProgress >= 0f && (i.toFloat() / count.toFloat()) <= playbackProgress) {
                playedBarPaint
            } else {
                barPaint
            }

            canvas.drawRoundRect(rectF, barWidth / 2f, barWidth / 2f, paint)
        }
    }
}
