package cock.crest.purrfectsnap.lite.core.util.hook

import java.lang.reflect.Member
import java.util.function.Consumer

@Suppress("UNCHECKED_CAST")
class HookAdapter(
    private val methodHookParam: Any
) {
    fun <T : Any> thisObject(): T {
        return YukiHookBridge.instance(methodHookParam) as T
    }

    fun <T : Any> nullableThisObject(): T? {
        return YukiHookBridge.instance(methodHookParam) as T?
    }

    fun method(): Member {
        return YukiHookBridge.member(methodHookParam)
    }

    fun <T : Any> arg(index: Int): T {
        return YukiHookBridge.args(methodHookParam)[index] as T
    }

    fun <T : Any> argNullable(index: Int): T? {
        return YukiHookBridge.args(methodHookParam).getOrNull(index) as T?
    }

    fun setArg(index: Int, value: Any?) {
        val args = YukiHookBridge.args(methodHookParam)
        if (index < 0 || index >= args.size) return
        args[index] = value
    }

    fun args(): Array<Any?> {
        return YukiHookBridge.args(methodHookParam)
    }

    fun getResult(): Any? {
        return YukiHookBridge.result(methodHookParam)
    }

    fun setResult(result: Any?) {
        YukiHookBridge.setResult(methodHookParam, result)
    }

    fun setThrowable(throwable: Throwable) {
        YukiHookBridge.setThrowable(methodHookParam, throwable)
    }

    fun clearThrowable() {
        YukiHookBridge.setThrowable(methodHookParam, null)
    }

    fun throwable(): Throwable? {
        return YukiHookBridge.throwable(methodHookParam)
    }

    fun invokeOriginal(): Any? {
        return YukiHookCompat.invokeOriginal(method(), nullableThisObject<Any>(), args())
    }

    fun invokeOriginal(args: Array<Any?>): Any? {
        return YukiHookCompat.invokeOriginal(method(), nullableThisObject<Any>(), args)
    }

    fun invokeOriginalSafe(errorCallback: Consumer<Throwable>) {
        invokeOriginalSafe(args(), errorCallback)
    }

    fun invokeOriginalSafe(args: Array<Any?>, errorCallback: Consumer<Throwable>) {
        runCatching {
            setResult(YukiHookCompat.invokeOriginal(method(), nullableThisObject<Any>(), args))
        }.onFailure {
            errorCallback.accept(it)
        }
    }
}
