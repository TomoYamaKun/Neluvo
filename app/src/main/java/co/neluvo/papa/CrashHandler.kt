//app/src/main/java/co/neluvo/papa/CrashHandler.kt
//ver 1.00-16
package co.neluvo.papa

import android.content.Context
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logContent = """
                ========================================
                CRASH LOG - ${AppVersion.getFullVersionInfo()}
                Time: $timestamp
                Thread: ${thread.name}
                ========================================
                $stackTrace
            """.trimIndent()

            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val logFile = File(dir, CRASH_LOG_FILE_NAME)
            logFile.writeText(logContent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        defaultHandler?.uncaughtException(thread, throwable) ?: run {
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    companion object {
        const val CRASH_LOG_FILE_NAME = "last_crash_log.txt"

        fun init(context: Context) {
            val handler = CrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }

        fun getSavedCrashLog(context: Context): String? {
            return try {
                val dir = context.getExternalFilesDir(null) ?: context.filesDir
                val logFile = File(dir, CRASH_LOG_FILE_NAME)
                if (logFile.exists() && logFile.length() > 0) {
                    logFile.readText()
                } else null
            } catch (e: Exception) {
                null
            }
        }

        fun clearCrashLog(context: Context) {
            try {
                val dir1 = context.getExternalFilesDir(null)
                if (dir1 != null) File(dir1, CRASH_LOG_FILE_NAME).delete()
                File(context.filesDir, CRASH_LOG_FILE_NAME).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
