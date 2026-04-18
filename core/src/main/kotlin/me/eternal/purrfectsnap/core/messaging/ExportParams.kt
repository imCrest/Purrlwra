package cock.crest.purrfectsnap.lite.core.messaging

import cock.crest.purrfectsnap.lite.common.data.ContentType

enum class ExportSortOrder {
    NEWEST_TO_OLDEST,
    OLDEST_TO_NEWEST
}

class ExportParams(
    val exportFormat: ExportFormat = ExportFormat.HTML,
    val sortOrder: ExportSortOrder = ExportSortOrder.NEWEST_TO_OLDEST,
    val messageTypeFilter: List<ContentType>? = null,
    val amountOfMessages: Int? = null,
    val downloadMedias: Boolean = false,
    val colorSeedHex: String? = null,
    val colorOverrides: Map<String, String>? = null,
)
