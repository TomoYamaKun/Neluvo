//==================================================
// FILE: /app/src/main/java/co/neluvo/papa/AmplitudeDbHelper.kt
// VER : 1.01-14
//==================================================
package co.neluvo.papa

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class AmplitudeRecord(
    val filename: String,
    val amplitude: Int,
    val timestamp: Long,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val manualLevel: Int = -1
)

data class DailyStat(
    val dateStr: String,
    val totalCount: Int,
    val quietCount: Int,
    val normalCount: Int,
    val snoreCount: Int,
    val heavyCount: Int
)

class AmplitudeDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "NeluvoPapa.db"
        private const val DATABASE_VERSION = 4
        private const val TABLE_NAME = "amplitude_log"
        private const val COLUMN_ID = "id"
        private const val COLUMN_FILENAME = "filename"
        private const val COLUMN_AMPLITUDE = "amplitude"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_BATTERY = "battery"
        private const val COLUMN_IS_CHARGING = "is_charging"
        private const val COLUMN_MANUAL_LEVEL = "manual_level"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        try {
            val createTable = """
                CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                    $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_FILENAME TEXT,
                    $COLUMN_AMPLITUDE INTEGER,
                    $COLUMN_TIMESTAMP INTEGER,
                    $COLUMN_BATTERY INTEGER,
                    $COLUMN_IS_CHARGING INTEGER,
                    $COLUMN_MANUAL_LEVEL INTEGER DEFAULT -1
                )
            """.trimIndent()
            db?.execSQL(createTable)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        try {
            if (oldVersion < 4) {
                db?.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_MANUAL_LEVEL INTEGER DEFAULT -1")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createTableIfNotExists() {
        try {
            val db = writableDatabase
            onCreate(db)
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun insertAmplitude(filename: String, amplitude: Int, batteryLevel: Int, isCharging: Int) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COLUMN_FILENAME, filename)
                put(COLUMN_AMPLITUDE, amplitude)
                put(COLUMN_TIMESTAMP, System.currentTimeMillis())
                put(COLUMN_BATTERY, batteryLevel)
                put(COLUMN_IS_CHARGING, isCharging)
                put(COLUMN_MANUAL_LEVEL, -1)
            }
            db.insert(TABLE_NAME, null, values)
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getAmplitudesForFile(filename: String): List<AmplitudeRecord> {
        val list = mutableListOf<AmplitudeRecord>()
        try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_NAME, null, "$COLUMN_FILENAME = ?", arrayOf(filename),
                null, null, "$COLUMN_TIMESTAMP ASC"
            )
            if (cursor.moveToFirst()) {
                do {
                    val amp = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_AMPLITUDE))
                    val ts = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                    val bat = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_BATTERY))
                    val chg = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_CHARGING)) == 1
                    val manLevel = try {
                        cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MANUAL_LEVEL))
                    } catch (e: Exception) { -1 }

                    list.add(AmplitudeRecord(filename, amp, ts, bat, chg, manLevel))
                } while (cursor.moveToNext())
            }
            cursor.close()
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    // 【変更】日付判定を12時間マイナス(43200000ms)して「お昼12時切り替え」に変更
    fun getDailyStats(startTimestamp: Long, endTimestamp: Long): List<DailyStat> {
        val list = mutableListOf<DailyStat>()
        try {
            val db = readableDatabase
            val query = """
                SELECT 
                  strftime('%Y-%m-%d', (timestamp - 43200000) / 1000, 'unixepoch', 'localtime') as day_date,
                  COUNT(*) as total_count,
                  SUM(CASE WHEN manual_level = 0 OR (manual_level = -1 AND amplitude < 1000) THEN 1 ELSE 0 END) as quiet_count,
                  SUM(CASE WHEN manual_level = 1 OR (manual_level = -1 AND amplitude >= 1000 AND amplitude < 3000) THEN 1 ELSE 0 END) as normal_count,
                  SUM(CASE WHEN manual_level = 2 OR (manual_level = -1 AND amplitude >= 3000 AND amplitude < 8000) THEN 1 ELSE 0 END) as snore_count,
                  SUM(CASE WHEN manual_level = 3 OR (manual_level = -1 AND amplitude >= 8000) THEN 1 ELSE 0 END) as heavy_count
                FROM amplitude_log
                WHERE timestamp >= ? AND timestamp <= ?
                GROUP BY day_date
                ORDER BY day_date ASC
            """.trimIndent()
            
            val cursor = db.rawQuery(query, arrayOf(startTimestamp.toString(), endTimestamp.toString()))
            if (cursor.moveToFirst()) {
                do {
                    val dateStr = cursor.getString(cursor.getColumnIndexOrThrow("day_date"))
                    val total = cursor.getInt(cursor.getColumnIndexOrThrow("total_count"))
                    val quiet = cursor.getInt(cursor.getColumnIndexOrThrow("quiet_count"))
                    val normal = cursor.getInt(cursor.getColumnIndexOrThrow("normal_count"))
                    val snore = cursor.getInt(cursor.getColumnIndexOrThrow("snore_count"))
                    val heavy = cursor.getInt(cursor.getColumnIndexOrThrow("heavy_count"))
                    
                    list.add(DailyStat(dateStr, total, quiet, normal, snore, heavy))
                } while (cursor.moveToNext())
            }
            cursor.close()
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun updateManualLevel(filename: String, targetTimestamp: Long, timeWindowMs: Long, newLevel: Int) {
        try {
            val db = writableDatabase
            val minTs = targetTimestamp - timeWindowMs
            val maxTs = targetTimestamp + timeWindowMs
            val values = ContentValues().apply {
                put(COLUMN_MANUAL_LEVEL, newLevel)
            }
            db.update(
                TABLE_NAME,
                values,
                "$COLUMN_FILENAME = ? AND $COLUMN_TIMESTAMP BETWEEN ? AND ?",
                arrayOf(filename, minTs.toString(), maxTs.toString())
            )
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteLogsForFile(filename: String) {
        try {
            val db = writableDatabase
            db.delete(TABLE_NAME, "$COLUMN_FILENAME = ?", arrayOf(filename))
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
