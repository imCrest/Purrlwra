package cock.crest.purrfectsnap.lite.core.scripting.impl

import cock.crest.purrfectsnap.lite.common.scripting.bindings.AbstractBinding
import cock.crest.purrfectsnap.lite.common.scripting.bindings.BindingSide
import cock.crest.purrfectsnap.lite.common.scripting.ktx.scriptableObject
import cock.crest.purrfectsnap.lite.common.scripting.toPrimitiveValue
import cock.crest.purrfectsnap.lite.core.util.hook.HookAdapter
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.Hooker
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.hook.hookConstructor
import org.mozilla.javascript.annotations.JSGetter
import org.mozilla.javascript.annotations.JSSetter
import java.lang.reflect.Constructor
import java.lang.reflect.Member
import java.lang.reflect.Method


class ScriptHookCallback(
    private val hookAdapter: HookAdapter
) {
    var result
        @JSGetter("result") get() = hookAdapter.getResult()
        @JSSetter("result") set(result) = hookAdapter.setResult(result.toPrimitiveValue(lazy {
            when (val member = hookAdapter.method()) {
                is Method -> member.returnType.name
                else -> "void"
            }
        }))

    val thisObject
        @JSGetter("thisObject") get() = hookAdapter.nullableThisObject<Any>()

    val method
        @JSGetter("method") get() = hookAdapter.method()

    val args
        @JSGetter("args") get() = hookAdapter.args().toList()

    private val parameterTypes by lazy {
        when (val member = hookAdapter.method()) {
            is Method -> member.parameterTypes
            is Constructor<*> -> member.parameterTypes
            else -> emptyArray()
        }.toList()
    }

    fun cancel() = hookAdapter.setResult(null)

    fun arg(index: Int) = hookAdapter.argNullable<Any>(index)

    fun setArg(index: Int, value: Any?) {
        hookAdapter.setArg(index, value.toPrimitiveValue(lazy { parameterTypes[index].name }))
    }

    fun invokeOriginal() = hookAdapter.invokeOriginal()

    fun invokeOriginal(args: Array<Any>) = hookAdapter.invokeOriginal(args.map {
        it.toPrimitiveValue(lazy { parameterTypes[args.indexOf(it)].name }) ?: it
    }.toTypedArray())

    override fun toString(): String {
        return "ScriptHookCallback(\n" +
                "  thisObject=${ runCatching { thisObject.toString() }.getOrNull() },\n" +
                "  args=${ runCatching { args.toString() }.getOrNull() }\n" +
                "  result=${ runCatching { result.toString() }.getOrNull() },\n" +
                ")"
    }
}


typealias HookCallback = (ScriptHookCallback) -> Unit
typealias HookUnhook = () -> Unit

@Suppress("unused")
class CoreScriptHooker: AbstractBinding("hooker", BindingSide.CORE) {
    private val hooks = mutableListOf<HookUnhook>()

    val stage = scriptableObject {
        putConst("BEFORE", this, "before")
        putConst("AFTER", this, "after")
    }

    private fun findClassSafe(className: String): Class<*>? {
        return runCatching {
            context.runtime.androidContext.classLoader.loadClass(className)
        }.onFailure {
            context.runtime.logger.warn("Failed to load class $className")
        }.getOrNull()
    }

    private fun getHookStageFromString(stage: String): HookStage {
        return when (stage) {
            "before" -> HookStage.BEFORE
            "after" -> HookStage.AFTER
            else -> throw IllegalArgumentException("Invalid stage: $stage")
        }
    }

    fun findMethod(clazz: Class<*>, methodName: String): Member? {
        return collectMethods(clazz).firstOrNull { it.name == methodName }
    }

    fun findMethodWithParameters(clazz: Class<*>, methodName: String, vararg types: String): Member? {
        return collectMethods(clazz).firstOrNull { method ->
            method.name == methodName && method.parameterTypes.map { it.name }.toTypedArray() contentEquals types
        }
    }

    fun findMethod(className: String, methodName: String): Member? {
        return findClassSafe(className)?.let { findMethod(it, methodName) }
    }

    fun findMethodWithParameters(className: String, methodName: String, vararg types: String): Member? {
        return findClassSafe(className)?.let { findMethodWithParameters(it, methodName, *types) }
    }

    fun findConstructor(clazz: Class<*>, vararg types: String): Member? {
        return clazz.declaredConstructors.find { constructor ->  constructor.parameterTypes.map { it.name }.toTypedArray() contentEquals types }
    }

    fun findConstructorParameters(className: String, vararg types: String): Member? {
        return findClassSafe(className)?.let { findConstructor(it, *types) }
    }

    // -- hooking

    fun hook(method: Member, stage: String, callback: HookCallback): HookUnhook {
        val hookAdapter = Hooker.hook(method, getHookStageFromString(stage)) {
            callback(ScriptHookCallback(it))
        }

        return {
            hookAdapter.unhook()
        }.also { hooks.add(it) }
    }

    fun hookAllMethods(clazz: Class<*>, methodName: String, stage: String, callback: HookCallback): HookUnhook {
        val hookAdapter = clazz.hook(methodName, getHookStageFromString(stage)) {
            callback(ScriptHookCallback(it))
        }

        return {
            hookAdapter.forEach { it.unhook() }
        }.also { hooks.add(it) }
    }

    fun hookAllConstructors(clazz: Class<*>, stage: String, callback: HookCallback): HookUnhook {
        val hookAdapter = clazz.hookConstructor(getHookStageFromString(stage)) {
            callback(ScriptHookCallback(it))
        }

        return {
            hookAdapter.forEach { it.unhook() }
        }.also { hooks.add(it) }
    }

    fun hookAllMethods(className: String, methodName: String, stage: String, callback: HookCallback)
        = findClassSafe(className)?.let { hookAllMethods(it, methodName, stage, callback) }

    fun hookAllConstructors(className: String, stage: String, callback: HookCallback)
        = findClassSafe(className)?.let { hookAllConstructors(it, stage, callback) }

    private fun collectMethods(clazz: Class<*>): List<Method> {
        val methods = LinkedHashMap<String, Method>()
        val visited = HashSet<Class<*>>()
        var currentClass: Class<*>? = clazz

        while (currentClass != null && currentClass != Any::class.java && currentClass != Object::class.java) {
            collectMethodsRecursive(currentClass, methods, visited)
            currentClass = currentClass.superclass
        }

        return methods.values.toList()
    }

    private fun collectMethodsRecursive(
        clazz: Class<*>,
        methods: MutableMap<String, Method>,
        visited: MutableSet<Class<*>>
    ) {
        if (!visited.add(clazz)) return

        clazz.declaredMethods.forEach { method ->
            val signature = buildString {
                append(method.name)
                append("#")
                method.parameterTypes.forEach {
                    append(it.name)
                    append(";")
                }
            }
            methods.putIfAbsent(signature, method)
        }

        clazz.interfaces.forEach { collectMethodsRecursive(it, methods, visited) }
    }

    override fun onDispose() {
        hooks.forEach { it() }
        hooks.clear()
    }

    override fun getObject() = this
}
