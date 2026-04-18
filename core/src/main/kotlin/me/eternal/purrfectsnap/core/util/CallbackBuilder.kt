package cock.crest.purrfectsnap.lite.core.util

import cock.crest.purrfectsnap.lite.core.util.hook.HookAdapter
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.Hooker
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Modifier

class CallbackBuilder(
    private val callbackClass: Class<*>
) {
    internal class Override(
        val methodName: String,
        val shouldUnhook: Boolean = true,
        val callback: (HookAdapter) -> Unit
    )

    private val methodOverrides = mutableListOf<Override>()

    fun override(
        methodName: String,
        shouldUnhook: Boolean = true,
        callback: (HookAdapter) -> Unit = {}
    ): CallbackBuilder {
        methodOverrides.add(Override(methodName, shouldUnhook, callback))
        return this
    }

    fun build(): Any {
        // get the first param of the first constructor to get the class of the invoker
        val rxEmitter: Class<*> = callbackClass.constructors.first().parameterTypes.first()
        // get the emitter field based on the class
        val rxEmitterField = callbackClass.fields.firstOrNull { field: Field ->
            field.type.isAssignableFrom(rxEmitter)
        } ?: error("No field found assignable from rxEmitter type!")
        // get the callback field based on the callback class
        val callbackInstance = createEmptyObject(callbackClass.constructors.first())!!
        val callbackInstanceHashCode: Int = callbackInstance.hashCode()
        val callbackInstanceClass = callbackInstance.javaClass
        val unhooks = mutableListOf<Hooker.HookHandle>()
        callbackInstanceClass.methods.forEach { method ->
            if (method.declaringClass != callbackInstanceClass) return@forEach
            if (Modifier.isPrivate(method.modifiers)) return@forEach
            // default hook that unhooks the callback and returns null
            val defaultHook: (HookAdapter) -> Boolean = defaultHook@{
                // ensure that's the callback was created by the CallbackBuilder
                if (rxEmitterField.get(it.thisObject()) != null) return@defaultHook false
                if ((it.thisObject() as Any).hashCode() != callbackInstanceHashCode) return@defaultHook false
                it.setResult(null)
                true
            }
            var hook: (HookAdapter) -> Unit = { defaultHook(it) }
            // override the default hook if the method is in the override list
            methodOverrides.find { it.methodName == method.name }?.run {
                hook = {
                    if (defaultHook(it)) {
                        callback(it)
                        if (shouldUnhook) unhooks.forEach { unhook -> unhook.unhook() }
                    }
                }
            }
            unhooks.add(Hooker.hook(method, HookStage.BEFORE, hook))
        }
        return callbackInstance
    }

    companion object {
        fun createEmptyObject(constructor: Constructor<*>): Any? {
            // compute the args for the constructor with null or default primitive values
            val args: Array<Any?> = constructor.parameterTypes.map { type: Class<*> ->
                if (type.isPrimitive) {
                    when (type.name) {
                        "boolean" -> false
                        "byte" -> 0.toByte()
                        "char" -> 0.toChar()
                        "short" -> 0.toShort()
                        "int" -> 0
                        "long" -> 0L
                        "float" -> 0f
                        "double" -> 0.0
                        else -> null
                    }
                } else null
            }.toTypedArray()
            return constructor.newInstance(*args)
        }
    }
}
