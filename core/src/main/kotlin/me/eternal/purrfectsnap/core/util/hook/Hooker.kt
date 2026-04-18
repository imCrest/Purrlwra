package cock.crest.purrfectsnap.lite.core.util.hook

import cock.crest.purrfectsnap.lite.common.logger.AbstractLogger
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Constructor
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.AccessibleObject

object Hooker {
    class HookHandle(private val unhooker: () -> Unit) {
        fun unhook() = unhooker()
    }

    inline fun newMethodHook(
        stage: HookStage,
        crossinline consumer: (HookAdapter) -> Unit,
        crossinline filter: ((HookAdapter) -> Boolean) = { true }
    ): Any {
        return YukiHookBridge.newMemberHook(
            { param ->
                if (stage == HookStage.BEFORE) {
                    runCatching {
                        HookAdapter(param).takeIf(filter)?.also(consumer)
                    }.onFailure {
                        AbstractLogger.directError("Failed to execute before hook", it)
                    }
                }
            },
            { param ->
                if (stage == HookStage.AFTER) {
                    runCatching {
                        HookAdapter(param).takeIf(filter)?.also(consumer)
                    }.onFailure {
                        AbstractLogger.directError("Failed to execute after hook", it)
                    }
                }
            }
        )
    }

    inline fun hook(
        clazz: Class<*>,
        methodName: String,
        stage: HookStage,
        crossinline filter: (HookAdapter) -> Boolean,
        noinline consumer: (HookAdapter) -> Unit
    ): Set<HookHandle> = collectMethods(clazz, methodName)
        .asSequence()
        .map { method ->
            method.isAccessible = true
            val hookResult = YukiHookCompat.hookMember(method, newMethodHook(stage, consumer, filter))
            HookHandle { YukiHookCompat.unhook(hookResult) }
        }.toMutableSet()

    inline fun hook(
        member: Member,
        stage: HookStage,
        crossinline filter: ((HookAdapter) -> Boolean),
        crossinline consumer: (HookAdapter) -> Unit
    ): HookHandle {
        (member as? AccessibleObject)?.isAccessible = true
        val hookResult = YukiHookCompat.hookMember(member, newMethodHook(stage, consumer, filter))
        return HookHandle { YukiHookCompat.unhook(hookResult) }
    }

    fun hook(
        clazz: Class<*>,
        methodName: String,
        stage: HookStage,
        consumer: (HookAdapter) -> Unit
    ): Set<HookHandle> = hook(clazz, methodName, stage, { true }, consumer)

    fun hook(
        member: Member,
        stage: HookStage,
        consumer: (HookAdapter) -> Unit
    ): HookHandle {
        return hook(member, stage, { true }, consumer)
    }

    fun hookConstructor(
        clazz: Class<*>,
        stage: HookStage,
        consumer: (HookAdapter) -> Unit
    ): Set<HookHandle> = clazz.declaredConstructors
        .asSequence()
        .map { constructor ->
            constructor.isAccessible = true
            val hookResult = YukiHookCompat.hookMember(constructor, newMethodHook(stage, consumer))
            HookHandle { YukiHookCompat.unhook(hookResult) }
        }.toMutableSet()

    fun hookConstructor(
        clazz: Class<*>,
        stage: HookStage,
        filter: ((HookAdapter) -> Boolean),
        consumer: (HookAdapter) -> Unit
    ) {
        clazz.declaredConstructors.forEach { constructor ->
            constructor.isAccessible = true
            YukiHookCompat.hookMember(constructor, newMethodHook(stage, consumer, filter))
        }
    }

    inline fun hookObjectMethod(
        clazz: Class<*>,
        instance: Any,
        methodName: String,
        stage: HookStage,
        crossinline hookConsumer: (HookAdapter) -> Unit
    ): List<() -> Unit> {
        val unhooks = mutableSetOf<HookHandle>()
        hook(clazz, methodName, stage) { param->
            if (param.nullableThisObject<Any>().let {
                if (it == null) unhooks.forEach { u -> u.unhook() }
                it != instance
            }) return@hook
            hookConsumer(param)
        }.also { unhooks.addAll(it) }
        return unhooks.map {
            { it.unhook() }
        }
    }

    inline fun ephemeralHook(
        clazz: Class<*>,
        methodName: String,
        stage: HookStage,
        crossinline hookConsumer: (HookAdapter) -> Unit
    ) {
        val unhooks: MutableSet<HookHandle> = HashSet()
        hook(clazz, methodName, stage) { param->
            hookConsumer(param)
            unhooks.forEach{ it.unhook() }
        }.also { unhooks.addAll(it) }
    }

    inline fun ephemeralHookObjectMethod(
        clazz: Class<*>,
        instance: Any,
        methodName: String,
        stage: HookStage,
        crossinline hookConsumer: (HookAdapter) -> Unit
    ): Set<() -> Unit> {
        val unhooks = mutableSetOf<HookHandle>()
        hook(clazz, methodName, stage) { param->
            if (param.nullableThisObject<Any>() != instance) return@hook
            unhooks.forEach { it.unhook() }
            hookConsumer(param)
        }.also { unhooks.addAll(it) }
        return unhooks.map {
            { it.unhook() }
        }.toSet()
    }

    inline fun ephemeralHookConstructor(
        clazz: Class<*>,
        stage: HookStage,
        crossinline hookConsumer: (HookAdapter) -> Unit
    ) {
        val unhooks: MutableSet<HookHandle> = HashSet()
        hookConstructor(clazz, stage) { param->
            hookConsumer(param)
            unhooks.forEach{ it.unhook() }
        }.also { unhooks.addAll(it) }
    }

    @PublishedApi
    internal fun collectMethods(clazz: Class<*>, methodName: String): List<Method> {
        val methods = LinkedHashMap<String, Method>()
        val visited = HashSet<Class<*>>()
        var currentClass: Class<*>? = clazz

        while (currentClass != null && currentClass != Any::class.java && currentClass != Object::class.java) {
            collectMethodsRecursive(currentClass, methodName, methods, visited)
            currentClass = currentClass.superclass
        }

        return methods.values.toList()
    }

    private fun collectMethodsRecursive(
        clazz: Class<*>,
        methodName: String,
        methods: MutableMap<String, Method>,
        visited: MutableSet<Class<*>>
    ) {
        if (!visited.add(clazz)) return

        clazz.declaredMethods
            .asSequence()
            .filter { it.name == methodName }
            .forEach { method ->
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

        clazz.interfaces.forEach { collectMethodsRecursive(it, methodName, methods, visited) }
    }
}

fun Class<*>.hookConstructor(
    stage: HookStage,
    consumer: (HookAdapter) -> Unit
) = Hooker.hookConstructor(this, stage, consumer)

fun Class<*>.hookConstructor(
    stage: HookStage,
    filter: ((HookAdapter) -> Boolean),
    consumer: (HookAdapter) -> Unit
) = Hooker.hookConstructor(this, stage, filter, consumer)

fun Class<*>.hook(
    methodName: String,
    stage: HookStage,
    consumer: (HookAdapter) -> Unit
): Set<Hooker.HookHandle> = Hooker.hook(this, methodName, stage, consumer)

fun Class<*>.hook(
    methodName: String,
    stage: HookStage,
    filter: (HookAdapter) -> Boolean,
    consumer: (HookAdapter) -> Unit
): Set<Hooker.HookHandle> = Hooker.hook(this, methodName, stage, filter, consumer)

fun Member.hook(
    stage: HookStage,
    consumer: (HookAdapter) -> Unit
): Hooker.HookHandle = Hooker.hook(this, stage, consumer)

fun Member.hook(
    stage: HookStage,
    filter: ((HookAdapter) -> Boolean),
    consumer: (HookAdapter) -> Unit
): Hooker.HookHandle = Hooker.hook(this, stage, filter, consumer)

fun Array<Method>.hookAll(stage: HookStage, param: (HookAdapter) -> Unit) {
    filter { it.declaringClass != Object::class.java && !Modifier.isAbstract(it.modifiers) }.forEach {
        it.hook(stage, param)
    }
}

fun Class<*>.findRestrictedMethod(
    predicate: (Method) -> Boolean
): Method? {
    return declaredMethods.find(predicate) ?: HiddenApiBypass.getDeclaredMethods(this).filterIsInstance<Method>().find(predicate)
}

fun Class<*>.findRestrictedConstructor(
    predicate: (Constructor<*>) -> Boolean
): Constructor<*>? {
    return declaredConstructors.find(predicate) ?: HiddenApiBypass.getDeclaredMethods(this).filterIsInstance<Constructor<*>>().find(predicate)
}
