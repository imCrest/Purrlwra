package cock.crest.purrfectsnap.lite.core.util

import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.core.util.ktx.setObjectField
import cock.crest.purrfectsnap.lite.core.util.ktx.KavaRefFieldBridge
import java.lang.reflect.Proxy


inline fun Any?.dataBuilder(dataClassBuilder: DataClassBuilder.() -> Unit): Any? {
    return DataClassBuilder(
        when (this) {
            is Class<*> -> CallbackBuilder.createEmptyObject(
                this.constructors.firstOrNull() ?: return null
            ) ?: return null
            else -> this
        } ?: return null
    ).apply(dataClassBuilder).build()
}

fun makeFunctionProxy(function: Any, handler: (args: Array<Any?>, originalCall: (Array<Any?>) -> Any?) -> Any?): Any {
    val type = function.javaClass.interfaces.firstOrNull() ?: function.javaClass
    val method = type.methods.firstOrNull {
        it.declaringClass == type
    }

    return Proxy.newProxyInstance(
        function::class.java.classLoader,
        arrayOf(type)
    ) { _, _, args ->
        handler(args) { newArgs ->
            method?.invoke(function, *newArgs)
        }
    }
}

// Util for building/editing data classes
class DataClassBuilder(
    private val instance: Any,
) {
    fun set(fieldName: String, value: Any?) {
        val fieldType = runCatching { KavaRefFieldBridge.getFieldType(instance, fieldName) }.getOrNull() ?: return

        when {
            fieldType.isEnum -> {
                val enumValue = fieldType.enumConstants?.firstOrNull { it.toString() == value } ?: return
                instance.setObjectField(fieldName, enumValue)
            }
            fieldType.isPrimitive -> {
                when (fieldType) {
                    Boolean::class.javaPrimitiveType -> instance.setObjectField(fieldName, value as Boolean)
                    Byte::class.javaPrimitiveType -> instance.setObjectField(fieldName, value as Byte)
                    Char::class.javaPrimitiveType -> instance.setObjectField(fieldName, value as Char)
                    Short::class.javaPrimitiveType -> instance.setObjectField(fieldName, value as Short)
                    Int::class.javaPrimitiveType -> instance.setObjectField(fieldName, value as Int)
                    Long::class.javaPrimitiveType -> instance.setObjectField(fieldName, value as Long)
                    Float::class.javaPrimitiveType -> instance.setObjectField(fieldName, value as Float)
                    Double::class.javaPrimitiveType -> instance.setObjectField(fieldName, value as Double)
                }
            }
            else -> instance.setObjectField(fieldName, value)
        }
    }

    fun set(vararg fields: Pair<String, Any?>) = fields.forEach { set(it.first, it.second) }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(fieldName: String): T? {
        return instance.getObjectField(fieldName) as? T
    }

    fun from(fieldName: String, new: Boolean = false, callback: DataClassBuilder.() -> Unit) {
        val fieldType = runCatching { KavaRefFieldBridge.getFieldType(instance, fieldName) }.getOrNull() ?: return

        val lazyInstance by lazy { CallbackBuilder.createEmptyObject(fieldType.constructors.firstOrNull() ?: return@lazy null) ?: return@lazy null }
        val builderInstance = if (new) lazyInstance else {
            instance.getObjectField(fieldName).takeIf { it != null } ?: lazyInstance
        }

        DataClassBuilder(builderInstance ?: return).apply(callback)

        instance.setObjectField(fieldName, builderInstance)
    }

    fun <T> cast(type: Class<T>, callback: T.() -> Unit) {
        type.cast(instance)?.let { callback(it) }
    }

    fun interceptFieldInterface(fieldName: String, callback: (args: Array<Any?>, originalCall: (Array<Any?>) -> Any?) -> Any?) {
        set(fieldName, instance.getObjectField(fieldName)?.let { makeFunctionProxy(it, callback) } ?: return)
    }

    fun build() = instance
}
