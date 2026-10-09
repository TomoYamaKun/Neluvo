//==================================================
// FILE: /app/src/main/java/co/neluvo/papa/RecordingService.kt
// VER : 1.01-25
//==================================================
package co.neluvo.papa

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var currentFileName = ""

    private lateinit var dbHelper: AmplitudeDbHelper
    private val handler = Handler(Looper.getMainLooper())

    private val amplitudeRunnable = object : Runnable {
        override fun run() {
            if (isRunning && mediaRecorder != null) {
                try {
                    val amp = mediaRecorder?.maxAmplitude ?: 0
                    
                    val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale) else -1
                    val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                    val isCharging = if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) 1 else 0

                    if (currentFileName.isNotEmpty()) {
                        dbHelper.insertAmplitude(currentFileName, amp, batteryPct, isCharging)
                    }

                    val intent = Intent(ACTION_AMPLITUDE_UPDATE).apply {
                        putExtra(EXTRA_AMPLITUDE, amp)
                        setPackage(packageName)
                    }
                    sendBroadcast(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                handler.postDelayed(this, 100)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            dbHelper = AmplitudeDbHelper(this)
            createNotificationChannel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        if (isRunning) return

        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        currentFileName = "neluvo_${sdf.format(Date())}.m4a"
        val outFile = File(getExternalFilesDir(null), currentFileName)

        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }

            isRunning = true
            startForeground(NOTIFICATION_ID, createNotification())
            handler.post(amplitudeRunnable)

        } catch (e: Exception) {
            e.printStackTrace()
            isRunning = false
            stopSelf()
        }
    }

    private fun stopRecording() {
        if (!isRunning) return
        isRunning = false
        handler.removeCallbacks(amplitudeRunnable)

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "録音サービス",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Neluvo Papa 録音中")
            .setContentText("音量をバックグラウンド記録中...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "co.neluvo.papa.ACTION_START"
        const val ACTION_STOP = "co.neluvo.papa.ACTION_STOP"
        const val ACTION_AMPLITUDE_UPDATE = "co.neluvo.papa.AMPLITUDE_UPDATE"
        const val EXTRA_AMPLITUDE = "extra_amplitude"
        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1001

        // 外部から録音中かどうかを判定できるフラグ
        var isRunning = false
            private set
    }
}
