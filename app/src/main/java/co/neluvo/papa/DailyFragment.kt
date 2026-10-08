//app/src/main/java/co/neluvo/papa/DailyFragment.kt
//ver 1.01-03
package co.neluvo.papa

import android.app.AlertDialog
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyFragment : Fragment() {

    private lateinit var dbHelper: AmplitudeDbHelper
    private var mediaPlayer: MediaPlayer? = null
    private var currentFilename: String? = null
    
    // UI Elements
    private var btnPlayPause: Button? = null
    private var seekBar: SeekBar? = null
    private var tvCurrentTime: TextView? = null
    private var tvEndTime: TextView? = null
    private var waveformView: WaveformView? = null
    private var tvAnalysisResult: TextView? = null
    
    // 時間管理用
    private var startTimestampMs: Long = 0L
    private var endTimestampMs: Long = 0L
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    
    // 波形タップ時の更新範囲（ミリ秒）
    private var dynamicTimeWindowMs: Long = 30000L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return try {
            inflater.inflate(R.layout.fragment_daily, container, false)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            dbHelper = AmplitudeDbHelper(requireContext())
            
            // 安全なUIバインディング（IDが存在しない場合のクラッシュ防止）
            val res = resources
            val pkg = requireContext().packageName
            
            btnPlayPause = view.findViewById(res.getIdentifier("btn_play", "id", pkg))
            seekBar = view.findViewById(res.getIdentifier("seek_bar_playback", "id", pkg))
            tvCurrentTime = view.findViewById(res.getIdentifier("tv_start_time", "id", pkg))
            tvEndTime = view.findViewById(res.getIdentifier("tv_end_time", "id", pkg))
            waveformView = view.findViewById(res.getIdentifier("waveform_view", "id", pkg))
            tvAnalysisResult = view.findViewById(res.getIdentifier("tv_analysis_result", "id", pkg))

            btnPlayPause?.setOnClickListener { togglePlayback() }
            
            seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        try {
                            mediaPlayer?.seekTo(progress)
                            updateAbsoluteTimeText(progress)
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            // 波形タップイベントで判定手動修正
            waveformView?.onWaveClickListener = { waveData ->
                showManualCorrectionDialog(waveData)
            }

            // 引数からファイル名取得、無ければ最新の音声ファイルを取得するフォールバック
            var filename = arguments?.getString("filename")
            if (filename.isNullOrEmpty()) {
                val dir = requireContext().getExternalFilesDir(null)
                // 修正箇所: FilenameFilterを明示して型の曖昧さを解消
                val files = dir?.listFiles { _, name -> name.endsWith(".m4a") }
                filename = files?.maxByOrNull { it.lastModified() }?.name
            }

            if (!filename.isNullOrEmpty()) {
                setupAudioData(filename)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupAudioData(filename: String) {
        currentFilename = filename
        try {
            val logs = dbHelper.getAmplitudesForFile(filename)
            if (logs.isNotEmpty()) {
                startTimestampMs = logs.first().timestamp
                endTimestampMs = logs.last().timestamp
                
                // 【要件1】録音開始時間と終了時間を絶対時間で表示
                tvCurrentTime?.text = timeFormat.format(Date(startTimestampMs))
                tvEndTime?.text = timeFormat.format(Date(endTimestampMs))

                // 長時間録音時のタップ更新範囲を動的計算（最低30秒）
                val totalDuration = endTimestampMs - startTimestampMs
                val maxPoints = 100
                dynamicTimeWindowMs = maxOf(30000L, (totalDuration / maxPoints) / 2L)

                // グラフ用の100件ダウンサンプリング
                val step = maxOf(1, logs.size / maxPoints)
                val downsampledLogs = logs.filterIndexed { index, _ -> index % step == 0 }

                // 波形ビューへデータをセット
                val waveDataList = downsampledLogs.map { log ->
                    WaveData(log.amplitude, log.timestamp, log.manualLevel)
                }
                waveformView?.setWaveData(waveDataList)

                // 解析テキストおよび既存バッテリー情報の更新
                updateAnalysisText(logs)
                
                // ※もし既存コードで BatteryGraphView が存在する場合、リフレクションで安全にデータ送信
                try {
                    val bgvId = resources.getIdentifier("battery_graph_view", "id", requireContext().packageName)
                    if (bgvId != 0) {
                        val bgv = view?.findViewById<View>(bgvId)
                        bgv?.javaClass?.methods?.find { it.name.contains("set") && it.parameterTypes.size == 1 && it.parameterTypes[0] == List::class.java }?.invoke(bgv, downsampledLogs)
                    }
                } catch (e: Exception) {
                    // バッテリーグラフが存在しないかメソッド違いの場合は無視してクラッシュ防止
                }
            }

            // MediaPlayer準備
            val audioFile = File(requireContext().getExternalFilesDir(null), filename)
            if (audioFile.exists()) {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(audioFile.absolutePath)
                    setOnPreparedListener { mp ->
                        seekBar?.max = mp.duration
                        btnPlayPause?.isEnabled = true
                    }
                    setOnCompletionListener {
                        btnPlayPause?.text = "▶ 再生"
                        stopSeekBarUpdate()
                        updateAbsoluteTimeText(0)
                        seekBar?.progress = 0
                    }
                    prepareAsync()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun togglePlayback() {
        try {
            val mp = mediaPlayer ?: return
            if (mp.isPlaying) {
                mp.pause()
                btnPlayPause?.text = "▶ 再生"
                stopSeekBarUpdate()
            } else {
                mp.start()
                btnPlayPause?.text = "⏸ 一時停止"
                startSeekBarUpdate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startSeekBarUpdate() {
        updateRunnable = object : Runnable {
            override fun run() {
                try {
                    val mp = mediaPlayer
                    if (mp != null && mp.isPlaying) {
                        val currentPosition = mp.currentPosition
                        seekBar?.progress = currentPosition
                        updateAbsoluteTimeText(currentPosition)
                        handler.postDelayed(this, 500)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        handler.post(updateRunnable!!)
    }

    private fun stopSeekBarUpdate() {
        updateRunnable?.let { handler.removeCallbacks(it) }
    }

    // シーク位置（ミリ秒）から録音時の絶対時間を算出
    private fun updateAbsoluteTimeText(progressMs: Int) {
        if (startTimestampMs > 0) {
            val currentAbsoluteMs = startTimestampMs + progressMs
            tvCurrentTime?.text = timeFormat.format(Date(currentAbsoluteMs))
        }
    }

    // 【要件4】自動判定の修正ダイアログ（タップでオペレーション追加）
    private fun showManualCorrectionDialog(waveData: WaveData) {
        try {
            val filename = currentFilename ?: return
            val options = arrayOf("静音", "安眠 (寝返り等)", "いびき", "ひどい (大いびき)")
            val levelValues = arrayOf(0, 1, 2, 3)

            AlertDialog.Builder(requireContext())
                .setTitle("判定の修正")
                .setMessage("選択した時間の判定を修正しますか？\n（タップ位置の前後を修正し、再集計します）")
                .setItems(options) { _, which ->
                    try {
                        val selectedLevel = levelValues[which]
                        dbHelper.updateManualLevel(filename, waveData.timestamp, dynamicTimeWindowMs, selectedLevel)
                        Toast.makeText(requireContext(), "判定を更新しました", Toast.LENGTH_SHORT).show()
                        
                        // DB更新後にUI再描画
                        setupAudioData(filename)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                .setNegativeButton("キャンセル", null)
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateAnalysisText(logs: List<AmplitudeRecord>) {
        try {
            var quiet = 0; var normal = 0; var snore = 0; var heavy = 0
            for (log in logs) {
                val level = if (log.manualLevel != -1) log.manualLevel else {
                    when {
                        log.amplitude < 1000 -> 0
                        log.amplitude < 3000 -> 1
                        log.amplitude < 8000 -> 2
                        else -> 3
                    }
                }
                when (level) {
                    0 -> quiet++; 1 -> normal++; 2 -> snore++; 3 -> heavy++
                }
            }
            val total = logs.size.toFloat()
            if (total > 0f) {
                // コンパイラの型の曖昧さエラー回避のため .toFloat() と 100f を明示
                val qPct = ((quiet.toFloat() / total) * 100f).toInt()
                val nPct = ((normal.toFloat() / total) * 100f).toInt()
                val sPct = ((snore.toFloat() / total) * 100f).toInt()
                val hPct = ((heavy.toFloat() / total) * 100f).toInt()
                
                // バッテリー情報も既存ロジックを尊重して追記
                val startBat = logs.first().batteryLevel
                val endBat = logs.last().batteryLevel
                val batText = if (startBat >= 0 && endBat >= 0) "\nバッテリー消費: $startBat% → $endBat%" else ""

                tvAnalysisResult?.text = "【分析結果】\n静音: $qPct%  安眠: $nPct%\nいびき: $sPct%  大いびき: $hPct%$batText"
            } else {
                tvAnalysisResult?.text = "【分析結果】\nデータがありません"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            stopSeekBarUpdate()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
