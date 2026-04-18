package cock.crest.purrfectsnap.lite.core.util.hook

import java.lang.reflect.Member

object YukiHookCompat {
    fun hookMember(member: Member, callback: Any): Any {
        return YukiHookBridge.hookMember(member, callback)
    }

    fun invokeOriginal(member: Member, instance: Any?, args: Array<Any?>): Any? {
        return YukiHookBridge.invokeOriginal(member, instance, args)
    }

    fun unhook(result: Any) {
        YukiHookBridge.unhook(result)
    }
}
