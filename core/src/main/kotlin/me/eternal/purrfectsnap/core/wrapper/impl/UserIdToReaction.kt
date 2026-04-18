package cock.crest.purrfectsnap.lite.core.wrapper.impl

import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.util.ktx.setObjectField
import cock.crest.purrfectsnap.lite.core.wrapper.AbstractWrapper
import org.mozilla.javascript.annotations.JSGetter
import org.mozilla.javascript.annotations.JSSetter

class UserIdToReaction(obj: Any?) : AbstractWrapper(obj) {
    @get:JSGetter @set:JSSetter
    var userId by field("mUserId") { SnapUUID(it) }
    @get:JSGetter @set:JSSetter
    var reactionId get() = (instanceNonNull().getObjectField("mReaction")
        ?.getObjectField("mReactionContent")
        ?.getObjectField("mIntentionType") as Long?) ?: -1
    set(value) {
        instanceNonNull().getObjectField("mReaction")
            ?.getObjectField("mReactionContent")
            ?.setObjectField("mIntentionType", value)
    }
}