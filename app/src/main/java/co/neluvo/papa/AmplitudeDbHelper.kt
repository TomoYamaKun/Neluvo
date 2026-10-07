//app/src/main/java/co/neluvo/papa/AmplitudeDbHelper.kt
//ver 1.00-28
package co.neluvo.papa

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class AmplitudeRecord(
    val id: Long,
    val fileName: String,
    val amplitude: Int,
    val timestamp: Long,
    val batteryLevel: Int,
    val isCharging: Int
)

class AmplitudeDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        createTableIfNotExists(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        createTableIfNotExists(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        createTableIfNotExists(db)
    }

    private fun createTableIfNotExists(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_FILE_NAME TEXT NOT NULL,
                $COLUMN_AMPLITUDE INTEGER NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_BATTERY_LEVEL INTEGER DEFAULT -1,
                $COLUMN_IS_CHARGING INTEGER DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    fun insertAmplitude(fileName: String, amplitude: Int, batteryLevel: Int, isCharging: Int) {
        try {
            val db = this.writableDatabase
            val values = ContentValues().apply {
                put(COLUMN_FILE_NAME, fileName)
                put(COLUMN_AMPLITUDE, amplitude)
                put(COLUMN_TIMESTAMP, System.currentTimeMillis())
                put(COLUMN_BATTERY_LEVEL, batteryLevel)
                put(COLUMN_IS_CHARGING, isCharging)
            }
            db.insert(TABLE_NAME, null, values)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getAmplitudesForFile(fileName: String): List<AmplitudeRecord> {
        val list = mutableListOf<AmplitudeRecord>()
        try {
            val db = this.readableDatabase
            createTableIfNotExists(db)

            val cursor = db.query(
                TABLE_NAME,
                null,
                "$COLUMN_FILE_NAME = ?",
                arrayOf(fileName),
                null,
                null,
                "$COLUMN_ID ASC"
            )

            cursor?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(COLUMN_ID)
                val fileIdx = c.getColumnIndexOrThrow(COLUMN_FILE_NAME)
                val ampIdx = c.getColumnIndexOrThrow(COLUMN_AMPLITUDE)
                val timeIdx = c.getColumnIndexOrThrow(COLUMN_TIMESTAMP)
                
                // バージョンアップで追加されたカラムが存在するかチェック
                val batIdx = c.getColumnIndex(COLUMN_BATTERY_LEVEL)
                val chargeIdx = c.getColumnIndex(COLUMN_IS_CHARGING)

                while (c.moveToNext()) {
                    val batLvl = if (batIdx != -1) c.getInt(batIdx) else -1
                    val isCharge = if (chargeIdx != -1) c.getInt(chargeIdx) else 0

                    list.add(
                        AmplitudeRecord(
                            id = c.getLong(idIdx),
                            fileName = c.getString(fileIdx),
                            amplitude = c.getInt(ampIdx),
                            timestamp = c.getLong(timeIdx),
                            batteryLevel = batLvl,
                            isCharging = isCharge
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    companion object {
        private const val DATABASE_NAME = "neluvo_amplitude.db"
        // カラム追加のためバージョンを3に繰り上げ（自動でマイグレーション）
        private const val DATABASE_VERSION = 3
        private const val TABLE_NAME = "amplitudes"
        private const val COLUMN_ID = "id"
        private const val COLUMN_FILE_NAME = "file_name"
        private const val COLUMN_AMPLITUDE = "amplitude"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_BATTERY_LEVEL = "battery_level"
        private const val COLUMN_IS_CHARGING = "is_charging"
    }
}
