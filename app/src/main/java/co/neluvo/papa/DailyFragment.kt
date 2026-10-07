//app/src/main/java/co/neluvo/papa/DailyFragment.kt
//ver 1.00-05
package co.neluvo.papa

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

class DailyFragment : Fragment() {

    private lateinit var spinnerFiles: Spinner
    private lateinit var tvFileInfo: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnPlay: Button
    private lateinit var btnStopPlay: Button

    private var fileList: List<File> = emptyList()
    private var selectedFile: File? = null

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false

    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    val currentPos = player.currentPosition
                    seekBar.progress = currentPos
                    tvCurrentTime.text = formatMs(currentPos)
                    handler.postDelayed(this, 500)
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

        spinnerFiles = view.findViewById(R.id.spinnerFiles)
        tvFileInfo = view.findViewById(R.id.tvFileInfo)
        seekBar = view.findViewById(R.id.seekBar)
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime)
        tvTotalTime = view.findViewById(R.id.tvTotalTime)
        btnPlay = view.findViewById(R.id.btnPlay)
        btnStopPlay = view.findViewById(R.id.btnStopPlay)

        setupListeners()
        loadAudioFiles()

        return view
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        @Suppress("DEPRECATION")
        super.setUserVisibleHint(isVisibleToUser)
        if (isVisibleToUser && ::spinnerFiles.isInitialized) {
            loadAudioFiles()
        }
    }

    override fun onResume() {
        super.onResume()
        loadAudioFiles()
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
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                listOf("（録音なし）")
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerFiles.adapter = adapter
            return
        }

        val fileNames = fileList.map { file ->
            val sizeKb = file.length() / 1024
            "${file.name} (${sizeKb} KB)"
        }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            fileNames
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFiles.adapter = adapter

        btnPlay.isEnabled = true
    }

    private fun setupListeners() {
        spinnerFiles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (fileList.isNotEmpty() && position < fileList.size) {
                    stopAudio()
                    selectedFile = fileList[position]
                    val sizeMb = String.format(Locale.getDefault(), "%.2f", selectedFile!!.length().toDouble() / (1024 * 1024))
                    tvFileInfo.text = "選択: ${selectedFile!!.name} ($sizeMb MB)"
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnPlay.setOnClickListener {
            if (isPlaying) {
                pauseAudio()
            } else {
                playAudio()
            }
        }

        btnStopPlay.setOnClickListener {
            stopAudio()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                    tvCurrentTime.text = formatMs(progress)
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
                setOnCompletionListener {
                    stopAudio()
                }
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
    }
}
