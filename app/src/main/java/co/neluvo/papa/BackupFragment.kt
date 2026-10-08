//==================================================
// FILE: /app/src/main/java/co/neluvo/papa/BackupFragment.kt
// VER : 1.01-21
//==================================================
package co.neluvo.papa

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupFragment : Fragment() {

    private lateinit var dbHelper: AmplitudeDbHelper
    private var storageBarView: StorageBarView? = null
    private var tvStorageDb: TextView? = null
    private var tvStorageAudio: TextView? = null
    private var tvStorageFree: TextView? = null
    private var tvCleanupLog: TextView? = null

    private val MAX_BYTES = 1024L * 1024L * 1024L // 1GB
    private val CREATE_ZIP_REQUEST_CODE = 1001
    private val RESTORE_ZIP_REQUEST_CODE = 1002

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return try {
            val pkg = requireContext().packageName
            val layoutId = resources.getIdentifier("fragment_backup", "layout", pkg)
            
            if (layoutId != 0) {
                inflater.inflate(layoutId, container, false)
            } else {
                TextView(requireContext()).apply { 
                    text = "【エラー】\nfragment_backup.xml が認識されていません。"
                    setTextColor(Color.RED)
                    setPadding(32, 32, 32, 32)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            TextView(requireContext()).apply { 
                text = "【レイアウト生成エラー】\n詳細: ${e.localizedMessage}"
                setTextColor(Color.RED)
                setPadding(32, 32, 32, 32)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            if (view is TextView) return

            dbHelper = AmplitudeDbHelper(requireContext())
            val pkg = requireContext().packageName

            val sbId = resources.getIdentifier("storageBarView", "id", pkg)
            if (sbId != 0) storageBarView = view.findViewById(sbId)

            val tdId = resources.getIdentifier("tvStorageDb", "id", pkg)
            if (tdId != 0) tvStorageDb = view.findViewById(tdId)

            val taId = resources.getIdentifier("tvStorageAudio", "id", pkg)
            if (taId != 0) tvStorageAudio = view.findViewById(taId)

            val tfId = resources.getIdentifier("tvStorageFree", "id", pkg)
            if (tfId != 0) tvStorageFree = view.findViewById(tfId)

            val tlId = resources.getIdentifier("tvCleanupLog", "id", pkg)
            if (tlId != 0) tvCleanupLog = view.findViewById(tlId)

            val btnCleanId = resources.getIdentifier("btnRunCleanup", "id", pkg)
            if (btnCleanId != 0) {
                view.findViewById<Button>(btnCleanId).setOnClickListener {
                    runCleanupAndCalculate()
                }
            }

            // バックアップボタン
            val btnBackupId = resources.getIdentifier("btnCloudBackup", "id", pkg)
            if (btnBackupId != 0) {
                view.findViewById<Button>(btnBackupId).setOnClickListener {
                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/zip"
                        putExtra(Intent.EXTRA_TITLE, "NeluvoPapa_Backup_$dateStr.zip")
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, CREATE_ZIP_REQUEST_CODE)
                }
            }

            // 【追加】リストア（復元）ボタン
            val btnRestoreId = resources.getIdentifier("btnRestoreBackup", "id", pkg)
            if (btnRestoreId != 0) {
                view.findViewById<Button>(btnRestoreId).setOnClickListener {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/zip"
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, RESTORE_ZIP_REQUEST_CODE)
                }
            }

            runCleanupAndCalculate()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && data?.data != null) {
            val uri = data.data!!
            if (requestCode == CREATE_ZIP_REQUEST_CODE) {
                performZipBackup(uri)
            } else if (requestCode == RESTORE_ZIP_REQUEST_CODE) {
                // 復元前にユーザーへ最終確認
                AlertDialog.Builder(requireContext())
                    .setTitle("復元の確認")
                    .setMessage("現在のデータはすべて上書きされ、消去されます。\n復元を実行してもよろしいですか？")
                    .setPositiveButton("復元する") { _, _ -> performZipRestore(uri) }
                    .setNegativeButton("キャンセル", null)
                    .show()
            }
        }
    }

    private fun performZipBackup(targetUri: Uri) {
        tvCleanupLog?.text = "[ログ] ZIPバックアップを作成中...\n(ファイル数によっては数分かかります)"
        
        Thread {
            try {
                val audioDir = requireContext().getExternalFilesDir(null)
                val audioFiles = audioDir?.listFiles { _, name -> name.endsWith(".m4a") } ?: emptyArray()

                val dbName = "NeluvoPapa.db"
                val dbFiles = listOf(
                    requireContext().getDatabasePath(dbName),
                    requireContext().getDatabasePath("$dbName-wal"),
                    requireContext().getDatabasePath("$dbName-shm"),
                    requireContext().getDatabasePath("$dbName-journal")
                )

                requireContext().contentResolver.openOutputStream(targetUri)?.use { os ->
                    ZipOutputStream(BufferedOutputStream(os)).use { zos ->
                        
                        // 1. データベースファイルの書き込み
                        for (dbFile in dbFiles) {
                            if (dbFile.exists()) {
                                zos.putNextEntry(ZipEntry("database/${dbFile.name}"))
                                FileInputStream(dbFile).use { fis -> fis.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }

                        // 2. 音声ファイルの書き込み
                        for (audioFile in audioFiles) {
                            zos.putNextEntry(ZipEntry("audio/${audioFile.name}"))
                            FileInputStream(audioFile).use { fis -> fis.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }

                Handler(Looper.getMainLooper()).post {
                    tvCleanupLog?.text = "[ログ] バックアップ完了！\n指定した場所にZIPを保存しました。"
                    Toast.makeText(requireContext(), "バックアップ完了", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    tvCleanupLog?.text = "[ログ] バックアップ失敗: ${e.localizedMessage}"
                }
            }
        }.start()
    }

    private fun performZipRestore(targetUri: Uri) {
        tvCleanupLog?.text = "[ログ] 復元中...\n(絶対にアプリを閉じないでください)"
        
        Thread {
            try {
                // 安全のため、上書き前にデータベースを閉じる
                dbHelper.close()

                requireContext().contentResolver.openInputStream(targetUri)?.use { inputStream ->
                    ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val fileName = File(entry.name).name
                            
                            if (entry.name.startsWith("database/")) {
                                val dbFile = requireContext().getDatabasePath(fileName)
                                FileOutputStream(dbFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                            } else if (entry.name.startsWith("audio/")) {
                                val audioDir = requireContext().getExternalFilesDir(null)
                                val audioFile = File(audioDir, fileName)
                                FileOutputStream(audioFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }

                // 復元成功後、アプリを再起動して再読み込みさせる
                Handler(Looper.getMainLooper()).post {
                    AlertDialog.Builder(requireContext())
                        .setTitle("復元完了")
                        .setMessage("データの復元が完了しました。\n設定を反映するため、アプリを再起動します。")
                        .setCancelable(false)
                        .setPositiveButton("OK") { _, _ ->
                            val intent = requireContext().packageManager.getLaunchIntentForPackage(requireContext().packageName)
                            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                            Runtime.getRuntime().exit(0)
                        }
                        .show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    tvCleanupLog?.text = "[ログ] 復元失敗: ${e.localizedMessage}"
                }
            }
        }.start()
    }

    private fun runCleanupAndCalculate() {
        tvCleanupLog?.text = "[ログ] ストレージ計算と整理を実行中..."
        Thread {
            try {
                val now = System.currentTimeMillis()
                val dir = requireContext().getExternalFilesDir(null)
                val files = dir?.listFiles { _, name -> name.endsWith(".m4a") } ?: emptyArray()

                var deletedCount = 0
                var keptCount = 0

                for (file in files) {
                    val ageDays = (now - file.lastModified()) / (1000 * 60 * 60 * 24)
                    
                    if (ageDays > 30) {
                        if (file.delete()) deletedCount++
                    } else if (ageDays in 8..30) {
                        val heavyPct = dbHelper.getHeavySnorePercentage(file.name)
                        if (heavyPct < 20f) {
                            if (file.delete()) deletedCount++
                        } else {
                            keptCount++
                        }
                    } else {
                        keptCount++
                    }
                }

                val oneYearMs = 365L * 24 * 60 * 60 * 1000L
                dbHelper.deleteOldData(now - oneYearMs)

                val updatedFiles = dir?.listFiles { _, name -> name.endsWith(".m4a") } ?: emptyArray()
                var audioSizeBytes = 0L
                for (f in updatedFiles) {
                    audioSizeBytes += f.length()
                }

                val dbFile = requireContext().getDatabasePath("NeluvoPapa.db")
                val dbSizeBytes = if (dbFile.exists()) dbFile.length() else 0L

                val totalUsed = audioSizeBytes + dbSizeBytes
                val freeSpace = maxOf(0L, MAX_BYTES - totalUsed)

                Handler(Looper.getMainLooper()).post {
                    storageBarView?.setStorageData(audioSizeBytes, dbSizeBytes)
                    
                    tvStorageDb?.text = "■ データベース: ${formatBytes(dbSizeBytes)}"
                    tvStorageAudio?.text = "■ 音声データ: ${formatBytes(audioSizeBytes)}"
                    tvStorageFree?.text = "■ 空き容量 (1GB上限): ${formatBytes(freeSpace)}"
                    
                    tvCleanupLog?.text = "[ログ] 整理完了\n保持したファイル: $keptCount 件\n削除したファイル: $deletedCount 件"
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    tvCleanupLog?.text = "[ログ] エラーが発生しました"
                }
            }
        }.start()
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.2f MB", mb)
    }
}
