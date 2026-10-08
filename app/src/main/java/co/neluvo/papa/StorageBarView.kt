//==================================================
// FILE: /app/src/main/java/co/neluvo/papa/StorageBarView.kt
// VER : 1.01-17
//==================================================
package co.neluvo.papa

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

// ストレージ占有率を描画するカスタムビュー（新規）
class StorageBarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var audioBytes: Long = 0
    private var dbBytes: Long = 0
    private val maxBytes: Long = 1024L * 1024L * 1024L // 上限 1GB

    private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E0E0E0") }
    private val paintAudio = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4CAF50") } // 緑
    private val paintDb = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2196F3") }    // 青

    fun setStorageData(audioSize: Long, dbSize: Long) {
        audioBytes = audioSize
        dbBytes = dbSize
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // 背景（全体：1GB分）を描画
        canvas.drawRoundRect(0f, 0f, w, h, 16f, 16f, paintBg)

        // DBとAudioの比率を計算
        val dbRatio = (dbBytes.toFloat() / maxBytes).coerceIn(0f, 1f)
        val audioRatio = (audioBytes.toFloat() / maxBytes).coerceIn(0f, 1f - dbRatio)

        val dbW = w * dbRatio
        val audioW = w * audioRatio

        // 左からDBを描画
        if (dbW > 0) {
            canvas.drawRoundRect(0f, 0f, dbW, h, 16f, 16f, paintDb)
        }
        // その横にAudioを描画
        if (audioW > 0) {
            canvas.drawRoundRect(dbW, 0f, dbW + audioW, h, 16f, 16f, paintAudio)
        }
    }
}
