package cock.crest.purrfectsnap.lite.common.util

import android.annotation.SuppressLint
import android.database.sqlite.SQLiteDatabase
import cock.crest.purrfectsnap.lite.common.logger.AbstractLogger

object SQLiteDatabaseHelper {
    @SuppressLint("Range")
    fun createTablesFromSchema(sqLiteDatabase: SQLiteDatabase, databaseSchema: Map<String, List<String>>) {
        databaseSchema.forEach { (tableName, columns) ->
            sqLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS $tableName (${columns.joinToString(", ")})")

            val cursor = sqLiteDatabase.rawQuery("PRAGMA table_info($tableName)", null)
            val existingColumns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                existingColumns.add(cursor.getString(cursor.getColumnIndex("name")) + " " + cursor.getString(cursor.getColumnIndex("type")))
            }
            cursor.close()

            val newColumns = columns.filter { column ->
                !column.uppercase().startsWith("PRIMARY KEY") && existingColumns.none { existingColumn -> column.startsWith(existingColumn) }
            }

            if (newColumns.isEmpty()) return@forEach

            AbstractLogger.directDebug("Schema for table $tableName has changed")
            sqLiteDatabase.execSQL("DROP TABLE $tableName")
            sqLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS $tableName (${columns.joinToString(", ")})")
        }
    }
}
