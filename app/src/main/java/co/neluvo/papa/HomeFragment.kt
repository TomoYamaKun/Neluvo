//app/src/main/java/co/neluvo/papa/HomeFragment.kt
//ver 1.00-24
package co.neluvo.papa

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
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

    private lateinit var chkTimer: CheckBox
    private lateinit var npHours: NumberPicker
    private lateinit var npMinutes: NumberPicker

    private lateinit var chkScreenOff: CheckBox
    private lateinit var npScreenOffSec: NumberPicker
    private lateinit var overlayScreenOff: FrameLayout

    private var isRecording = false

    private val screenOffHandler = Handler(Looper.getMainLooper())
    private var screenOffRunnable: Runnable? = null

    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

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

        chkTimer = view.findViewById(R.id.chkTimer)
        npHours = view.findViewById(R.id.npHours)
        npMinutes = view.findViewById(R.id.npMinutes)

        chkScreenOff = view.findViewById(R.id.chkScreenOff)
        npScreenOffSec = view.findViewById(R.id.npScreenOffSec)
        overlayScreenOff = view.findViewById(R.id.overlayScreenOff)

        npHours.minValue = 0
        npHours.maxValue = 23
        npMinutes.minValue = 0
        npMinutes.maxValue = 59
        npMinutes.value = 30

        npScreenOffSec.minValue = 3
        npScreenOffSec.maxValue = 60
        npScreenOffSec.value = 5

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

        overlayScreenOff.setOnClickListener {
            wakeScreen()
        }

        checkAndShowCrashLog()

        return view
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(RecordingService.ACTION_AMPLITUDE_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Context.RECEIVER_NOT_EXPORTED の直接値である 4 を使用
            requireContext().registerReceiver(amplitudeReceiver, filter, 4)
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

    private fun startRecordingService() {
        val intent = Intent(requireContext(), RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
        }
        ContextCompat.startForegroundService(requireContext(), intent)
        isRecording = true
        waveformView.clear()
        updateUi()

        if (chkTimer.isChecked) {
            val totalMillis = ((npHours.value * 3600) + (npMinutes.value * 60)) * 1000L
            if (totalMillis > 0) {
                timerRunnable = Runnable {
                    stopRecordingService()
                    Toast.makeText(requireContext(), "タイマーにより録音を自動停止しました", Toast.LENGTH_LONG).show()
                }
                timerHandler.postDelayed(timerRunnable!!, totalMillis)
            }
        }

        if (chkScreenOff.isChecked) {
            val sec = npScreenOffSec.value
            screenOffRunnable = Runnable {
                turnOffScreen()
            }
            screenOffHandler.postDelayed(screenOffRunnable!!, sec * 1000L)
        }
    }

    private fun stopRecordingService() {
        screenOffRunnable?.let { screenOffHandler.removeCallbacks(it) }
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        wakeScreen()

        val intent = Intent(requireContext(), RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        requireContext().startService(intent)
        isRecording = false
        updateUi()
    }

    private fun turnOffScreen() {
        if (!isRecording) return
        activity?.window?.let { window ->
            val lp = window.attributes
            lp.screenBrightness = 0.01f
            window.attributes = lp
        }
        overlayScreenOff.visibility = View.VISIBLE
    }

    private fun wakeScreen() {
        activity?.window?.let { window ->
            val lp = window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
        }
        overlayScreenOff.visibility = View.GONE
    }

    private fun updateUi() {
        if (isRecording) {
            tvStatus.text = "録音中..."
            btnStart.isEnabled = false
            btnStop.isEnabled = true
            chkTimer.isEnabled = false
            npHours.isEnabled = false
            npMinutes.isEnabled = false
            chkScreenOff.isEnabled = false
            npScreenOffSec.isEnabled = false
            tvFilePath.text = "保存フォルダ: " + requireContext().getExternalFilesDir(null)?.absolutePath
        } else {
            tvStatus.text = "停止中"
            btnStart.isEnabled = true
            btnStop.isEnabled = false
            chkTimer.isEnabled = true
            npHours.isEnabled = true
            npMinutes.isEnabled = true
            chkScreenOff.isEnabled = true
            npScreenOffSec.isEnabled = true
        }
    }

    private fun checkAndShowCrashLog() {
        val crashLog = CrashHandler.getSavedCrashLog(requireContext())
        if (!crashLog.isNullOrEmpty()) {
            val fullText = "${AppVersion.getFullVersionInfo()}\n\n$crashLog"
            AlertDialog.Builder(requireContext())
                .setTitle("⚠️ 前回のクラッシュログ")
                .setMessage("$fullText\n\n(※コピーボタンまたはタップでコピー)")
                .setPositiveButton("📋 コピー") { _, _ ->
                    copyToClipboard(fullText)
                }
                .setNeutralButton("ログ消去") { dialog, _ ->
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
            .setTitle("アプリ情報 / 設定")
            .setMessage(msg)
            .setPositiveButton("🎨 アイコン変更") { _, _ ->
                showIconSelectDialog()
            }
            .setNeutralButton("📋 ログコピー") { _, _ ->
                copyToClipboard(msg)
            }
            .setNegativeButton("閉じる", null)
            .show()
    }

    private fun showIconSelectDialog() {
        val items = arrayOf("🌙 月デザイン", "🌊 波デザイン")
        AlertDialog.Builder(requireContext())
            .setTitle("アプリアイコンの選択")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> changeAppIcon(true)
                    1 -> changeAppIcon(false)
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun changeAppIcon(useMoonIcon: Boolean) {
        val context = requireContext()
        val pm = context.packageManager
        val moonAlias = ComponentName(context, "co.neluvo.papa.MainActivityMoon")
        val waveAlias = ComponentName(context, "co.neluvo.papa.MainActivityWave")

        try {
            if (useMoonIcon) {
                pm.setComponentEnabledSetting(moonAlias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(waveAlias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            } else {
                pm.setComponentEnabledSetting(waveAlias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(moonAlias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
            Toast.makeText(context, "アイコンを変更しました！", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("App Log", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "ログをコピーしました", Toast.LENGTH_SHORT).show()
    }

    private fun checkPermissions(): Boolean {
        val recordAudioGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        return recordAudioGranted && notificationGranted
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissions(permissions.toTypedArray(), 200)
    }
}
