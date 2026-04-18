package cock.crest.purrfectsnap.lite.common.database.impl

import android.database.Cursor
import cock.crest.purrfectsnap.lite.common.database.DatabaseObject
import cock.crest.purrfectsnap.lite.common.util.ktx.getStringOrNull

data class StorySnapEntry(
    var rawSnapId: String? = null,
    var mediaUrl: String? = null,
    var mediaKey: String? = null,
    var mediaIv: String? = null,
) : DatabaseObject {
    override fun write(cursor: Cursor) {
        with(cursor) {
            rawSnapId = getStringOrNull("rawSnapId")!!
            mediaUrl = getStringOrNull("mediaUrl")
            mediaKey = getStringOrNull("mediaKey")?.takeIf { it.isNotEmpty() }
            mediaIv = getStringOrNull("mediaIv")?.takeIf { it.isNotEmpty() }
        }
    }
}