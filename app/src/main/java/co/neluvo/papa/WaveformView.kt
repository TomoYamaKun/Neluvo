//app/src/main/java/co/neluvo/papa/WaveformView.kt
//ver 1.00-11
package co.neluvo.papa

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.jvm.JvmOverloads

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val waveList = mutableListOf<Int>()
    private var maxAmplitude = 32767f
    private var playbackProgressRatio: Float = -1f

    private val wavePaint = Paint().apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val progressPaint = Paint().apply {
        color = Color.parseColor("#F44336")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val centerLinePaint = Paint().apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    fun setWaveData(data: List<Int>) {
        waveList.clear()
        waveList.addAll(data)
        invalidate()
    }

    fun addAmplitude(amp: Int) {
        waveList.add(amp)
        if (waveList.size > 50) {
            waveList.removeAt(0)
        }
        invalidate()
    }

    fun setPlaybackProgress(ratio: Float) {
        playbackProgressRatio = ratio
        invalidate()
    }

    fun clear() {
        waveList.clear()
        playbackProgressRatio = -1f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val centerY = h / 2f

        canvas.drawLine(0f, centerY, w, centerY, centerLinePaint)

        if (waveList.isEmpty()) return

        val count = waveList.size
        val barWidth = w / count.coerceAtLeast(1)

        for (i in 0 until count) {
            val amp = waveList[i].coerceAtMost(32767)
            val normalized = (amp / maxAmplitude).coerceIn(0.04f, 1.0f)
            val barHeight = (h / 2f) * normalized

            val x = i * barWidth
            val left = x + (barWidth * 0.15f)
            val right = x + (barWidth * 0.85f)
            val top = centerY - barHeight
            val bottom = centerY + barHeight

            canvas.drawRect(left, top, right, bottom, wavePaint)
        }

        if (playbackProgressRatio in 0.0f..1.0f) {
            val posX = w * playbackProgressRatio
            canvas.drawLine(posX, 0f, posX, h, progressPaint)
        }
    }
}
