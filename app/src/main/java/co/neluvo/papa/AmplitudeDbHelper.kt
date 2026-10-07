//app/src/main/java/co/neluvo/papa/AmplitudeDbHelper.kt
//ver 1.00-07
package co.neluvo.papa

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AmplitudeDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTableSql = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_FILE_NAME TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_AMPLITUDE INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTableSql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertAmplitude(fileName: String, timestamp: Long, amplitude: Int) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_FILE_NAME, fileName)
            put(COLUMN_TIMESTAMP, timestamp)
            put(COLUMN_AMPLITUDE, amplitude)
        }
        db.insert(TABLE_NAME, null, values)
    }

    fun getAmplitudesForFile(fileName: String): List<AmplitudeRecord> {
        val recordList = mutableListOf<AmplitudeRecord>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_NAME,
            arrayOf(COLUMN_TIMESTAMP, COLUMN_AMPLITUDE),
            "$COLUMN_FILE_NAME = ?",
            arrayOf(fileName),
            null,
            null,
            "$COLUMN_TIMESTAMP ASC"
        )

        cursor.use { c ->
            val timeIdx = c.getColumnIndexOrThrow(COLUMN_TIMESTAMP)
            val ampIdx = c.getColumnIndexOrThrow(COLUMN_AMPLITUDE)
            while (c.moveToNext()) {
                val timestamp = c.getLong(timeIdx)
                val amplitude = c.getInt(ampIdx)
                recordList.add(AmplitudeRecord(timestamp, amplitude))
            }
        }
        return recordList
    }

    data class AmplitudeRecord(val timestamp: Long, val amplitude: Int)

    companion object {
        private const val DATABASE_NAME = "neluvo_amplitude.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_NAME = "amplitude_logs"
        const val COLUMN_ID = "id"
        const val COLUMN_FILE_NAME = "file_name"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_AMPLITUDE = "amplitude"
    }
}
