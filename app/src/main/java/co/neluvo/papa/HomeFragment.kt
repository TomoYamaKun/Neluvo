//app/src/main/java/co/neluvo/papa/HomeFragment.kt
//ver 1.00-06
package co.neluvo.papa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {

    private lateinit var tvStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvFilePath: TextView

    private var isRecording = false

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

        return view
    }

    private fun startRecordingService() {
        val intent = Intent(requireContext(), RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
        }
        ContextCompat.startForegroundService(requireContext(), intent)
        isRecording = true
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
