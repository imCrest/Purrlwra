package cock.crest.purrfectsnap.lite.common.database

import android.database.Cursor

interface DatabaseObject {
    fun write(cursor: Cursor)
}
