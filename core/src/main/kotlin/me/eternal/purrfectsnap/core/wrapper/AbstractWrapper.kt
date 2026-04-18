package cock.crest.purrfectsnap.lite.core.wrapper

import cock.crest.purrfectsnap.lite.core.util.CallbackBuilder
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.util.ktx.setObjectField
import cock.crest.purrfectsnap.lite.core.wrapper.impl.SnapUUID
import kotlin.reflect.KProperty

abstract class AbstractWrapper(
    protected open var instance: Any?
) {
    protected val uuidArrayListMapper: (Any?) -> ArrayList<SnapUUID> get() = { (it as ArrayList<*>).map { i -> SnapUUID(i) }.toCollection(ArrayList()) }

    @Suppress("UNCHECKED_CAST")
    inner class EnumAccessor<T>(private val fieldName: String, private val defaultValue: T) {
        operator fun getValue(obj: Any, property: KProperty<*>): T? = getEnumValue(fieldName, defaultValue as Enum<*>) as? T
        operator fun setValue(obj: Any, property: KProperty<*>, value: Any?) = setEnumValue(fieldName, value as Enum<*>)
    }

    inner class FieldAccessor<T>(private val fieldName: String, private val mapper: ((Any?) -> T?)? = null) {
        @Suppress("UNCHECKED_CAST")
        operator fun getValue(obj: Any, property: KProperty<*>): T? {
            return runCatching {
                val value = instance?.getObjectField(fieldName)
                mapper?.invoke(value) ?: value as? T
            }.getOrNull()
        }

        operator fun setValue(obj: Any, property: KProperty<*>, value: Any?) {
            instance?.setObjectField(fieldName, when (value) {
                is AbstractWrapper -> value.instance
                is ArrayList<*> -> value.map { if (it is AbstractWrapper) it.instance else it }.toMutableList()
                else -> value
            })
        }
    }

    companion object {
        fun newEmptyInstance(clazz: Class<*>): Any {
            return CallbackBuilder.createEmptyObject(clazz.constructors[0]) ?: throw NullPointerException()
        }
    }

    fun instanceNonNull(): Any = instance ?: throw NullPointerException("Instance of ${this::class.simpleName} is null")
    fun isPresent(): Boolean = instance != null

    override fun hashCode(): Int {
        return instance.hashCode()
    }

    override fun toString(): String {
        return instance.toString()
    }

    protected fun <T> enum(fieldName: String, defaultValue: T) = EnumAccessor(fieldName, defaultValue)
    protected fun <T> field(fieldName: String, mapper: ((Any?) -> T?)? = null) = FieldAccessor(fieldName, mapper)

    fun <T : Enum<*>> getEnumValue(fieldName: String, defaultValue: T?): T? {
        if (defaultValue == null || instance == null) return null
        val mContentType = instance?.getObjectField(fieldName) as? Enum<*> ?: return null
        return java.lang.Enum.valueOf(defaultValue::class.java, mContentType.name)
    }

    @Suppress("UNCHECKED_CAST")
    fun setEnumValue(fieldName: String, value: Enum<*>) {
        val currentValue = instance?.getObjectField(fieldName) as? Enum<*> ?: return
        @Suppress("UNCHECKED_CAST")
        val type = currentValue.javaClass as Class<out Enum<*>>
        instance?.setObjectField(fieldName, java.lang.Enum.valueOf(type, value.name))
    }
}
