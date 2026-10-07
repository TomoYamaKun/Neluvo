//app/src/main/java/co/neluvo/papa/HomeFragment.kt
//ver 1.00-12
package co.neluvo.papa

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {

    private lateinit var tvStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvFilePath: TextView
    private lateinit var tvVersion: TextView
    private lateinit var btnAbout: Button
    private lateinit var waveformView: WaveformView

    private var isRecording = false

    private val amplitudeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == RecordingService.ACTION_AMPLITUDE_UPDATE) {
                val amplitude = intent.getIntExtra(RecordingService.EXTRA_AMPLITUDE, 0)
                waveformView.addAmplitude(amplitude)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        tvStatus = view.findViewById(R.id.tvStatus)
        btnStart = view.findViewById(R.id.btnStart)
        btnStop = view.findViewById(R.id.btnStop)
        tvFilePath = view.findViewById(R.id.tvFilePath)
        tvVersion = view.findViewById(R.id.tvVersion)
        btnAbout = view.findViewById(R.id.btnAbout)
        waveformView = view.findViewById(R.id.waveformView)

        tvVersion.text = "v" + AppVersion.VERSION_NAME

        btnStart.setOnClickListener {
            if (checkPermissions()) {
                startRecordingService()
            } else {
                requestPermissions()
            }
        }

        btnStop.setOnClickListener {
            stopRecordingService()
        }

        btnAbout.setOnClickListener {
            showAboutDialog()
        }

        checkAndShowCrashLog()

        return view
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(RecordingService.ACTION_AMPLITUDE_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(amplitudeReceiver, filter, 0)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            requireContext().registerReceiver(amplitudeReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            requireContext().unregisterReceiver(amplitudeReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkAndShowCrashLog() {
        val crashLog = CrashHandler.getSavedCrashLog(requireContext())
        if (!crashLog.isNullOrEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle("⚠️ 前回のクラッシュログ")
                .setMessage(crashLog)
                .setPositiveButton("ログ消去") { dialog, _ ->
                    CrashHandler.clearCrashLog(requireContext())
                    dialog.dismiss()
                }
                .setNegativeButton("閉じる", null)
                .show()
        }
    }

    private fun showAboutDialog() {
        val crashLog = CrashHandler.getSavedCrashLog(requireContext()) ?: "エラーログなし (正常)"
        val msg = "${AppVersion.getFullVersionInfo()}\n\n【直近の動作ログ】\n$crashLog"

        AlertDialog.Builder(requireContext())
            .setTitle("アプリ情報 / Help")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .setNeutralButton("ログ消去") { _, _ ->
                CrashHandler.clearCrashLog(requireContext())
            }
            .show()
    }

    private fun startRecordingService() {
        val intent = Intent(requireContext(), RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
        }
        ContextCompat.startForegroundService(requireContext(), intent)
        isRecording = true
        waveformView.clear()
        updateUi()
    }

    private fun stopRecordingService() {
        val intent = Intent(requireContext(), RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        requireContext().startService(intent)
        isRecording = false
        updateUi()
    }

    private fun updateUi() {
        if (isRecording) {
            tvStatus.text = "録音中..."
            btnStart.isEnabled = false
            btnStop.isEnabled = true
            tvFilePath.text = "保存フォルダ: " + requireContext().getExternalFilesDir(null)?.absolutePath
        } else {
            tvStatus.text = "停止中"
            btnStart.isEnabled = true
            btnStop.isEnabled = false
        }
    }

    private fun checkPermissions(): Boolean {
        val recordAudioGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return recordAudioGranted && notificationGranted
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissions(permissions.toTypedArray(), REQUEST_CODE_PERMISSIONS)
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 200
    }
}
