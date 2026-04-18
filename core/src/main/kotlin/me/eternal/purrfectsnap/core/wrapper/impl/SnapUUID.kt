package cock.crest.purrfectsnap.lite.core.wrapper.impl

import cock.crest.purrfectsnap.lite.core.PurrfectSnap
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.wrapper.AbstractWrapper
import java.nio.ByteBuffer
import java.util.UUID

fun String.toSnapUUID() = SnapUUID(this)
fun ByteArray.toSnapUUID() = SnapUUID(this)

fun UUID.toBytes(): ByteArray =
    ByteBuffer.allocate(16).let {
        it.putLong(this.mostSignificantBits)
        it.putLong(this.leastSignificantBits)
        it.array()
    }

class SnapUUID(
    private val obj: Any?
) : AbstractWrapper(obj) {
    private fun extractUuidBytesFromObject(any: Any): ByteArray? {
        runCatching { any.getObjectField("mId") as? ByteArray }.getOrNull()?.let { return it }
        runCatching { any.javaClass.getMethod("getId").invoke(any) as? ByteArray }.getOrNull()?.let { return it }
        runCatching { any.javaClass.getMethod("getUuid").invoke(any) as? ByteArray }.getOrNull()?.let { return it }
        runCatching { any.javaClass.getMethod("uuid").invoke(any) as? ByteArray }.getOrNull()?.let { return it }
        return null
    }

    private val uuidBytes by lazy {
        when {
            obj is String -> {
                UUID.fromString(obj).toBytes()
            }
            obj is ByteArray -> {
                assert(obj.size == 16)
                obj
            }
            obj is UUID -> obj.toBytes()
            PurrfectSnap.classCache.snapUUID.isInstance(obj) -> {
                obj?.getObjectField("mId") as ByteArray
            }
            PurrfectSnap.classCache.snapShimsUUID?.isInstance(obj) == true -> {
                val any = obj as Any
                runCatching { any.javaClass.getMethod("getId").invoke(any) as ByteArray }.getOrElse {
                    any.getObjectField("mId") as ByteArray
                }
            }
            obj is Any -> {
                extractUuidBytesFromObject(obj)
                    ?: runCatching { UUID.fromString(obj.toString()).toBytes() }.getOrElse { ByteArray(16) }
            }
            else -> ByteArray(16)
        }
    }

    private val uuidString by lazy { ByteBuffer.wrap(uuidBytes).run { UUID(long, long) }.toString() }

    override var instance: Any?
        set(_) {}
        get() = PurrfectSnap.classCache.snapUUID.getConstructor(ByteArray::class.java).newInstance(uuidBytes)

    override fun toString(): String {
        return uuidString
    }

    fun toBytes() = uuidBytes

    override fun equals(other: Any?): Boolean {
        return other is SnapUUID && other.uuidBytes.contentEquals(this.uuidBytes)
    }

    override fun hashCode(): Int {
        return uuidBytes.contentHashCode()
    }
}

