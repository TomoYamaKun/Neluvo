//app/src/main/java/co/neluvo/papa/WaveformView.kt
//ver 1.01-04
package co.neluvo.papa

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

data class WaveData(val amplitude: Int, val timestamp: Long, val manualLevel: Int = -1)

class WaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
    }

    private val waveDataList = mutableListOf<WaveData>()
    private var barWidth = 10f
    private var spaceWidth = 4f
    
    var onWaveClickListener: ((WaveData) -> Unit)? = null

    private val colorQuiet = Color.parseColor("#4CAF50")
    private val colorNormal = Color.parseColor("#03A9F4")
    private val colorSnore = Color.parseColor("#FFEB3B")
    private val colorHeavySnore = Color.parseColor("#F44336")

    fun setWaveData(list: List<WaveData>) {
        try {
            waveDataList.clear()
            waveDataList.addAll(list)
            invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // HomeFragment等からのリアルタイム追加用インターフェースの復旧
    fun addAmplitude(amplitude: Int) {
        try {
            waveDataList.add(WaveData(amplitude, System.currentTimeMillis(), -1))
            // リアルタイム描画でのメモリ肥大化を防ぐため、一定数を超えたら古いデータを削除
            if (waveDataList.size > 150) {
                waveDataList.removeAt(0)
            }
            invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 既存処理（HomeFragment等）が使用するクリア用メソッドの復旧
    fun clear() {
        try {
            waveDataList.clear()
            invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        try {
            if (waveDataList.isEmpty()) return

            val width = width.toFloat()
            val height = height.toFloat()
            val centerY = height / 2f
            
            val totalBars = waveDataList.size
            if (totalBars > 0) {
                val availableWidth = width / totalBars
                barWidth = availableWidth * 0.7f
                spaceWidth = availableWidth * 0.3f
            }

            for (i in waveDataList.indices) {
                val data = waveDataList[i]
                val startX = i * (barWidth + spaceWidth)
                
                val maxAmp = 32767f
                val ratio = (data.amplitude.toFloat() / maxAmp).coerceIn(0.01f, 1f)
                val barHeight = (height * 0.8f) * ratio
                
                val level = if (data.manualLevel != -1) {
                    data.manualLevel
                } else {
                    when {
                        data.amplitude < 1000 -> 0
                        data.amplitude < 3000 -> 1
                        data.amplitude < 8000 -> 2
                        else -> 3
                    }
                }

                paint.color = when (level) {
                    0 -> colorQuiet
                    1 -> colorNormal
                    2 -> colorSnore
                    3 -> colorHeavySnore
                    else -> colorQuiet
                }

                paint.strokeWidth = barWidth
                canvas.drawLine(
                    startX + barWidth / 2f, 
                    centerY - barHeight / 2f, 
                    startX + barWidth / 2f, 
                    centerY + barHeight / 2f, 
                    paint
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        try {
            if (event.action == MotionEvent.ACTION_UP) {
                val totalWidth = barWidth + spaceWidth
                val tappedIndex = (event.x / totalWidth).toInt()
                
                if (tappedIndex in waveDataList.indices) {
                    val tappedData = waveDataList[tappedIndex]
                    onWaveClickListener?.invoke(tappedData)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return true
    }
}
