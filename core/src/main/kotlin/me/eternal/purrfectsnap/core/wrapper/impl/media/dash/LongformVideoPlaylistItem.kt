package cock.crest.purrfectsnap.lite.core.wrapper.impl.media.dash

import cock.crest.purrfectsnap.lite.core.util.ktx.findFieldNamesByType
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.wrapper.AbstractWrapper

class LongformVideoPlaylistItem(obj: Any?) : AbstractWrapper(obj) {
    private val chapterListField by lazy {
        instanceNonNull().findFieldNamesByType(List::class.java).first()
    }
    val chapters: List<SnapChapter>
        get() = (instanceNonNull().getObjectField(chapterListField) as List<*>).map { SnapChapter(it) }
}
