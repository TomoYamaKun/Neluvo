//app/src/main/java/co/neluvo/papa/DailyFragment.kt
//ver 1.00-29
package co.neluvo.papa

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
import androidx.fragment.app.Fragment
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class DailyFragment : Fragment() {

    private lateinit var spinnerFiles: Spinner
    private lateinit var tvFileInfo: TextView
    private lateinit var waveformDaily: WaveformView
    private lateinit var batteryGraphView: BatteryGraphView
    private lateinit var pieChartView: PieChartView
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnPlay: Button
    private lateinit var btnStopPlay: Button

    private lateinit var seekBarVolume: SeekBar
    private lateinit var btnMute: Button
    private lateinit var tvAnalysis: TextView

    private var fileList: List<File> = emptyList()
    private var selectedFile: File? = null

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false

    private lateinit var audioManager: AudioManager
    private var isMuted = false
    private var previousVolume = 0

    private lateinit var dbHelper: AmplitudeDbHelper
    private val dbExecutor = Executors.newSingleThreadExecutor()

    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    val currentPos = player.currentPosition
                    val duration = player.duration
                    seekBar.progress = currentPos
                    tvCurrentTime.text = formatMs(currentPos)

                    if (duration > 0) {
                        val ratio = currentPos.toFloat() / duration.toFloat()
                        waveformDaily.setPlaybackProgress(ratio)
                    }

                    handler.postDelayed(this, 300)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_daily, container, false)

        dbHelper = AmplitudeDbHelper(requireContext())
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager

        spinnerFiles = view.findViewById(R.id.spinnerFiles)
        tvFileInfo = view.findViewById(R.id.tvFileInfo)
        waveformDaily = view.findViewById(R.id.waveformDaily)
        batteryGraphView = view.findViewById(R.id.batteryGraphView)
        pieChartView = view.findViewById(R.id.pieChartView)
        seekBar = view.findViewById(R.id.seekBar)
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime)
        tvTotalTime = view.findViewById(R.id.tvTotalTime)
        btnPlay = view.findViewById(R.id.btnPlay)
        btnStopPlay = view.findViewById(R.id.btnStopPlay)

        seekBarVolume = view.findViewById(R.id.seekBarVolume)
        btnMute = view.findViewById(R.id.btnMute)
        tvAnalysis = view.findViewById(R.id.tvAnalysis)

        setupVolumeControl()
        setupListeners()
        loadAudioFiles()

        return view
    }

    private fun setupVolumeControl() {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        seekBarVolume.max = maxVol
        seekBarVolume.progress = curVol

        seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                    if (progress > 0 && isMuted) {
                        isMuted = false
                        btnMute.text = "消音"
                    }
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnMute.setOnClickListener {
            if (isMuted) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0)
                seekBarVolume.progress = previousVolume
                isMuted = false
                btnMute.text = "消音"
            } else {
                previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                seekBarVolume.progress = 0
                isMuted = true
                btnMute.text = "解除"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadAudioFiles()
        if (::seekBarVolume.isInitialized) {
            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            seekBarVolume.progress = curVol
        }
    }

    private fun loadAudioFiles() {
        val dir = requireContext().getExternalFilesDir(null)
        fileList = dir?.listFiles { _, name ->
            name.startsWith("neluvo_") && name.endsWith(".m4a")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()

        if (fileList.isEmpty()) {
            tvFileInfo.text = "録音データが見つかりません"
            btnPlay.isEnabled = false
            btnStopPlay.isEnabled = false
            waveformDaily.clear()
            batteryGraphView.clear()
            tvAnalysis.text = "データなし"
            pieChartView.setData(0, 0, 0, 0)
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, listOf("（録音なし）"))
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerFiles.adapter = adapter
            return
        }

        val fileNames = fileList.map { file ->
            val sizeKb = file.length() / 1024
            "${file.name} (${sizeKb} KB)"
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, fileNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFiles.adapter = adapter

        btnPlay.isEnabled = true
    }

    private fun loadGraphAndAnalysis(fileName: String) {
        dbExecutor.execute {
            val records = dbHelper.getAmplitudesForFile(fileName)
            
            if (records.isEmpty()) {
                handler.post {
                    waveformDaily.clear()
                    batteryGraphView.clear()
                    pieChartView.setData(0, 0, 0, 0)
                    tvAnalysis.text = "※このファイルの履歴データはありません"
                }
                return@execute
            }

            // メモリ保護: 最大100件に間引く（波形とバッテリー推移の両方に使用）
            val maxPoints = 100
            val sampledRecords = if (records.size > maxPoints) {
                val step = records.size.toFloat() / maxPoints
                (0 until maxPoints).map { i ->
                    records[(i * step).toInt().coerceAtMost(records.size - 1)]
                }
            } else {
                records
            }

            val sampledAmps = sampledRecords.map { it.amplitude }
            val sampledBattery = sampledRecords.map { BatteryGraphView.DataPoint(it.batteryLevel, it.isCharging == 1) }

            // 分析計算は間引き前の全データ(records)で行う
            var quietCount = 0
            var lowCount = 0
            var midCount = 0
            var highCount = 0

            for (r in records) {
                when {
                    r.amplitude < 1000 -> quietCount++
                    r.amplitude < 5000 -> lowCount++
                    r.amplitude < 15000 -> midCount++
                    else -> highCount++
                }
            }

            val total = records.size.coerceAtLeast(1)
            val qPct = quietCount * 100 / total
            val lPct = lowCount * 100 / total
            val mPct = midCount * 100 / total
            val hPct = highCount * 100 / total

            val firstRecord = records.first()
            val lastRecord = records.last()
            var batteryInfo = ""
            if (firstRecord.batteryLevel >= 0) {
                val startCharge = if (firstRecord.isCharging == 1) "⚡充電中" else "🔋"
                val endCharge = if (lastRecord.isCharging == 1) "⚡充電中" else "🔋"
                batteryInfo = "\n\n【バッテリー消費】\n開始: ${firstRecord.batteryLevel}% ($startCharge) ➔ 終了: ${lastRecord.batteryLevel}% ($endCharge)"
            }

            val analysisText = """
                ・静寂 (安眠): $qPct% ($quietCount 秒)
                ・小 (寝返り/小音): $lPct% ($lowCount 秒)
                ・中 (中いびき): $mPct% ($midCount 秒)
                ・大 (大いびき): $hPct% ($highCount 秒)$batteryInfo
            """.trimIndent()

            handler.post {
                waveformDaily.setWaveData(sampledAmps)
                batteryGraphView.setData(sampledBattery)
                pieChartView.setData(quietCount, lowCount, midCount, highCount)
                tvAnalysis.text = analysisText
            }
        }
    }

    private fun setupListeners() {
        spinnerFiles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (fileList.isNotEmpty() && position < fileList.size) {
                    stopAudio()
                    selectedFile = fileList[position]
                    val sizeMb = String.format(Locale.getDefault(), "%.2f", selectedFile!!.length().toDouble() / (1024 * 1024))
                    tvFileInfo.text = "選択: ${selectedFile!!.name} ($sizeMb MB)"
                    loadGraphAndAnalysis(selectedFile!!.name)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnPlay.setOnClickListener {
            if (isPlaying) pauseAudio() else playAudio()
        }

        btnStopPlay.setOnClickListener { stopAudio() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                    tvCurrentTime.text = formatMs(progress)
                    mediaPlayer?.let { player ->
                        if (player.duration > 0) {
                            waveformDaily.setPlaybackProgress(progress.toFloat() / player.duration.toFloat())
                        }
                    }
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun playAudio() {
        val file = selectedFile ?: return

        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener { stopAudio() }
            }
            seekBar.max = mediaPlayer!!.duration
            tvTotalTime.text = formatMs(mediaPlayer!!.duration)
        }

        mediaPlayer?.start()
        isPlaying = true
        btnPlay.text = "一時停止"
        btnStopPlay.isEnabled = true
        handler.post(updateProgressRunnable)
    }

    private fun pauseAudio() {
        mediaPlayer?.pause()
        isPlaying = false
        btnPlay.text = "再生"
        handler.removeCallbacks(updateProgressRunnable)
    }

    private fun stopAudio() {
        handler.removeCallbacks(updateProgressRunnable)
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        isPlaying = false
        btnPlay.text = "再生"
        btnStopPlay.isEnabled = false
        seekBar.progress = 0
        tvCurrentTime.text = "00:00:00"
        waveformDaily.setPlaybackProgress(-1f)
    }

    private fun formatMs(ms: Int): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopAudio()
        dbExecutor.shutdown()
    }
}
