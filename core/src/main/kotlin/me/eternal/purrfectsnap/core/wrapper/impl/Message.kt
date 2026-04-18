package cock.crest.purrfectsnap.lite.core.wrapper.impl

import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.common.data.MessageState
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoReader
import cock.crest.purrfectsnap.lite.core.wrapper.AbstractWrapper
import org.mozilla.javascript.annotations.JSGetter
import org.mozilla.javascript.annotations.JSSetter


fun ByteArray.getMessageText(contentType: ContentType): String? {
    val protoReader by lazy { ProtoReader(this) }
    val raw = when (contentType) {
        ContentType.CHAT -> protoReader.getString(2, 1) ?: "Failed to parse message"
        ContentType.TINY_SNAP -> protoReader.getString(19, 1, 1)
        ContentType.EXTERNAL_MEDIA -> protoReader.getString(7, 11, 1)
        ContentType.STORY_REPLY -> protoReader.getString(7, 11, 1)
        ContentType.SNAP -> protoReader.followPath(11, 5)?.run {
            val captions = mutableListOf<String>()

            eachBuffer(1) {
                followPath(4) {
                    val caption = getString(3, 2, 1)
                    if (caption != null) {
                        captions.add(caption)
                    }
                }
            }

            captions.takeIf { it.isNotEmpty() }?.joinToString("\n")
        }
        else -> null
    }
    return raw?.sanitizeForLayout()
}

fun String.sanitizeForLayout(): String {
    var changed = false
    val builder = StringBuilder(length)
    var index = 0
    while (index < length) {
        val char = this[index]
        if (Character.isHighSurrogate(char)) {
            if (index + 1 < length && Character.isLowSurrogate(this[index + 1])) {
                builder.append(char).append(this[index + 1])
                index += 2
                continue
            }
            builder.append('\uFFFD')
            changed = true
            index++
            continue
        }
        if (Character.isLowSurrogate(char)) {
            builder.append('\uFFFD')
            changed = true
            index++
            continue
        }
        builder.append(char)
        index++
    }
    return if (changed) builder.toString() else this
}

class Message(obj: Any?) : AbstractWrapper(obj) {
    @get:JSGetter @set:JSSetter
    var orderKey by field<Long>("mOrderKey")
    @get:JSGetter @set:JSSetter
    var senderId by field("mSenderId") { SnapUUID(it) }
    @get:JSGetter @set:JSSetter
    var messageContent by field("mMessageContent") { MessageContent(it) }
    @get:JSGetter @set:JSSetter
    var messageDescriptor by field("mDescriptor") { MessageDescriptor(it) }
    @get:JSGetter @set:JSSetter
    var messageMetadata by field("mMetadata") { MessageMetadata(it) }
    @get:JSGetter @set:JSSetter
    var messageState by enum("mState", MessageState.COMMITTED)

    fun serialize(): String?{
        return  messageContent?.content?.getMessageText(messageContent?.contentType ?: return null)
    }
}
