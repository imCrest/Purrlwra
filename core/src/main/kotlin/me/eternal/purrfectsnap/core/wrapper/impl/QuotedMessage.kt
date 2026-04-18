package cock.crest.purrfectsnap.lite.core.wrapper.impl

import cock.crest.purrfectsnap.lite.common.data.QuotedMessageContentStatus
import cock.crest.purrfectsnap.lite.core.wrapper.AbstractWrapper
import org.mozilla.javascript.annotations.JSGetter
import org.mozilla.javascript.annotations.JSSetter

class QuotedMessage(obj: Any?) : AbstractWrapper(obj) {
    @get:JSGetter @set:JSSetter
    var content by field("mContent") { QuotedMessageContent(it) }
    @get:JSGetter
    val status by enum("mStatus", QuotedMessageContentStatus.UNKNOWN)
}