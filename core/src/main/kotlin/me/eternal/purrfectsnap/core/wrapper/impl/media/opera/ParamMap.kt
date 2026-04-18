package cock.crest.purrfectsnap.lite.core.wrapper.impl.media.opera

import cock.crest.purrfectsnap.lite.common.util.ktx.findFields
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.wrapper.AbstractWrapper
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

@Suppress("UNCHECKED_CAST")
class ParamMap(obj: Any?) : AbstractWrapper(obj) {
    val paramMapField: Field by lazy {
        instanceNonNull()::class.java.findFields(once = true) {
            it.type == ConcurrentHashMap::class.java || runCatching { it.get(instance) }.getOrNull() is ConcurrentHashMap<*, *>
        }.firstOrNull() ?: throw RuntimeException("Could not find paramMap field")
    }

    val concurrentHashMap: ConcurrentHashMap<Any, Any>
        get() = instanceNonNull().getObjectField(paramMapField.name) as ConcurrentHashMap<Any, Any>

    operator fun get(key: String): Any? {
        return concurrentHashMap.keys.firstOrNull{ k: Any -> k.toString() == key }?.let { concurrentHashMap[it] }
    }

    fun put(key: String, value: Any) {
        val keyObject = concurrentHashMap.keys.firstOrNull { k: Any -> k.toString() == key } ?: key
        concurrentHashMap[keyObject] = value
    }

    fun containsKey(key: String): Boolean {
        return concurrentHashMap.keys.any { k: Any -> k.toString() == key }
    }

    fun getStoryIdentity(): String? {
        return this["STORY_ID"]?.toString()
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?: this["TOPIC_SNAP_CREATOR_USER_ID"]?.toString()
                ?.takeIf { it.isNotBlank() && it != "null" }
            ?: this["STORY_SNAP_ID"]?.toString()
                ?.substringBefore("_")
                ?.takeIf { it.isNotBlank() && it != "null" }
            ?: this["PLAYLIST_V2_GROUP"]?.toString()
                ?.substringAfter("storyUserId=", "")
                ?.substringBefore(",")
                ?.takeIf { it.isNotBlank() && it != "null" }
            ?: this["PLAYABLE_STORY_SNAP_RECORD"]?.toString()
                ?.substringAfter("storyUserId=", "")
                ?.substringBefore(",")
                ?.takeIf { it.isNotBlank() && it != "null" }
    }

    fun getStorySnapIndex(): Int? {
        return (this["STORY_SNAP_INDEX"] as? Int)
            ?: (this["snap_index_in_story"]?.toString()?.toIntOrNull())
            ?: (this["SNAP_POSITION_IN_STORY"]?.toString()?.toIntOrNull())
            ?: (this["REPLAYABLE_STORY_SNAP_RECORD"]?.toString()?.substringAfter("snapIndex=", "")?.substringBefore(",")?.toIntOrNull())
    }

    fun getStorySnapTotal(): Int {
        return (this["STORY_SNAP_TOTAL"] as? Int)
            ?: (this["snap_story_length"]?.toString()?.toIntOrNull())
            ?: (this["NUM_SNAPS_IN_STORY"]?.toString()?.toIntOrNull())
            ?: 0
    }

    override fun toString(): String {
        return concurrentHashMap.toString()
    }
}
