//==================================================
// FILE: /app/src/main/java/co/neluvo/papa/DailyFragment.kt
// VER : 1.01-09
//==================================================
package co.neluvo.papa

import android.app.AlertDialog
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
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
    private var spinnerFiles: Spinner? = null
    private var tvFileInfo: TextView? = null
    private var waveformDaily: WaveformView? = null
    private var seekBar: SeekBar? = null
    private var tvCurrentTime: TextView? = null
    private var tvTotalTime: TextView? = null
    private var btnPlay: Button? = null
    private var btnStopPlay: Button? = null
    private var seekBarVolume: SeekBar? = null
    private var btnMute: Button? = null
    private var tvAnalysis: TextView? = null
    
    private var batteryGraphView: BatteryGraphView? = null
    // 型を専用クラスに明示
    private var pieChartView: PieChartView? = null

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
            
            spinnerFiles = view.findViewById(R.id.spinnerFiles)
            tvFileInfo = view.findViewById(R.id.tvFileInfo)
            waveformDaily = view.findViewById(R.id.waveformDaily)
            seekBar = view.findViewById(R.id.seekBar)
            tvCurrentTime = view.findViewById(R.id.tvCurrentTime)
            tvTotalTime = view.findViewById(R.id.tvTotalTime)
            btnPlay = view.findViewById(R.id.btnPlay)
            btnStopPlay = view.findViewById(R.id.btnStopPlay)
            seekBarVolume = view.findViewById(R.id.seekBarVolume)
            btnMute = view.findViewById(R.id.btnMute)
            tvAnalysis = view.findViewById(R.id.tvAnalysis)
            batteryGraphView = view.findViewById(R.id.batteryGraphView)
            pieChartView = view.findViewById(R.id.pieChartView)

            setupVolumeControls()
            loadFileListIntoSpinner()

            btnPlay?.setOnClickListener { togglePlayback() }
            btnStopPlay?.setOnClickListener { stopPlayback() }
            
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

            waveformDaily?.onWaveClickListener = { waveData ->
                showManualCorrectionDialog(waveData)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadFileListIntoSpinner() {
        try {
            val dir = requireContext().getExternalFilesDir(null)
            val files = dir?.listFiles { _, name -> name.endsWith(".m4a") }
            if (files != null && files.isNotEmpty()) {
                val fileNames = files.sortedByDescending { it.lastModified() }.map { it.name }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, fileNames)
                spinnerFiles?.adapter = adapter

                spinnerFiles?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val selectedFile = fileNames[position]
                        setupAudioData(selectedFile)
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            } else {
                tvFileInfo?.text = "録音データがありません"
                btnPlay?.isEnabled = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupVolumeControls() {
        try {
            val audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

            seekBarVolume?.max = maxVolume
            seekBarVolume?.progress = currentVolume

            seekBarVolume?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            btnMute?.setOnClickListener {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                seekBarVolume?.progress = 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupAudioData(filename: String) {
        currentFilename = filename
        try {
            val audioFile = File(requireContext().getExternalFilesDir(null), filename)
            val fileSizeBytes = audioFile.length()
            val fileSizeMb = String.format(Locale.US, "%.2f MB", fileSizeBytes / (1024.0 * 1024.0))
            tvFileInfo?.text = "ファイル名: $filename\nサイズ: $fileSizeMb"

            val logs = dbHelper.getAmplitudesForFile(filename)
            if (logs.isNotEmpty()) {
                startTimestampMs = logs.first().timestamp
                endTimestampMs = logs.last().timestamp
                
                tvCurrentTime?.text = timeFormat.format(Date(startTimestampMs))
                tvTotalTime?.text = timeFormat.format(Date(endTimestampMs))

                val totalDuration = endTimestampMs - startTimestampMs
                val maxPoints = 100
                dynamicTimeWindowMs = maxOf(30000L, (totalDuration / maxPoints) / 2L)

                val step = maxOf(1, logs.size / maxPoints)
                val downsampledLogs = logs.filterIndexed { index, _ -> index % step == 0 }

                val waveDataList = downsampledLogs.map { log ->
                    WaveData(log.amplitude, log.timestamp, log.manualLevel)
                }
                waveformDaily?.setWaveData(waveDataList)

                updateAnalysisText(logs)
                
                // バッテリーグラフへデータ送信
                try {
                    val batteryDataPoints = downsampledLogs.map { log ->
                        BatteryGraphView.DataPoint(log.batteryLevel, log.isCharging)
                    }
                    batteryGraphView?.setData(batteryDataPoints)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (audioFile.exists()) {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(audioFile.absolutePath)
                    setOnPreparedListener { mp ->
                        seekBar?.max = mp.duration
                        btnPlay?.isEnabled = true
                    }
                    setOnCompletionListener {
                        stopPlayback()
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
                btnPlay?.text = "再生"
                stopSeekBarUpdate()
            } else {
                mp.start()
                btnPlay?.text = "一時停止"
                btnStopPlay?.isEnabled = true
                startSeekBarUpdate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopPlayback() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.pause()
                it.seekTo(0)
            }
            btnPlay?.text = "再生"
            btnStopPlay?.isEnabled = false
            stopSeekBarUpdate()
            updateAbsoluteTimeText(0)
            seekBar?.progress = 0
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

    private fun updateAbsoluteTimeText(progressMs: Int) {
        if (startTimestampMs > 0) {
            val currentAbsoluteMs = startTimestampMs + progressMs
            tvCurrentTime?.text = timeFormat.format(Date(currentAbsoluteMs))
        }
    }

    private fun showManualCorrectionDialog(waveData: WaveData) {
        try {
            val filename = currentFilename ?: return
            val options = arrayOf("静音", "安眠 (寝返り等)", "いびき", "ひどい (大いびき)")
            val levelValues = arrayOf(0, 1, 2, 3)

            AlertDialog.Builder(requireContext())
                .setTitle("判定の修正")
                .setMessage("選択した時間の判定を修正しますか？")
                .setItems(options) { _, which ->
                    try {
                        val selectedLevel = levelValues[which]
                        dbHelper.updateManualLevel(filename, waveData.timestamp, dynamicTimeWindowMs, selectedLevel)
                        Toast.makeText(requireContext(), "判定を更新しました", Toast.LENGTH_SHORT).show()
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
            
            // 円グラフへ集計データをセット
            pieChartView?.setData(quiet, normal, snore, heavy)
            
            val total = logs.size.toFloat()
            if (total > 0f) {
                val qPct = ((quiet.toFloat() / total) * 100f).toInt()
                val nPct = ((normal.toFloat() / total) * 100f).toInt()
                val sPct = ((snore.toFloat() / total) * 100f).toInt()
                val hPct = ((heavy.toFloat() / total) * 100f).toInt()
                
                val startBat = logs.first().batteryLevel
                val endBat = logs.last().batteryLevel
                val batText = if (startBat >= 0 && endBat >= 0) "\nバッテリー消費: $startBat% → $endBat%" else ""

                tvAnalysis?.text = "【分析結果】\n静音: $qPct%  安眠: $nPct%\nいびき: $sPct%  大いびき: $hPct%$batText"
            } else {
                tvAnalysis?.text = "【分析結果】\nデータがありません"
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
