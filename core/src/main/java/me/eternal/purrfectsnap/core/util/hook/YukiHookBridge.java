package cock.crest.purrfectsnap.lite.core.util.hook;

import com.highcapable.yukihookapi.hook.core.api.helper.YukiHookHelper;
import com.highcapable.yukihookapi.hook.core.api.proxy.YukiHookCallback;
import com.highcapable.yukihookapi.hook.core.api.proxy.YukiMemberHook;
import com.highcapable.yukihookapi.hook.core.api.result.YukiHookResult;

import java.lang.reflect.Member;

public final class YukiHookBridge {
    private YukiHookBridge() {}

    public interface ParamCallback {
        void accept(Object param);
    }

    public static Object newMemberHook(ParamCallback before, ParamCallback after) {
        return new YukiMemberHook() {
            @Override
            public void beforeHookedMember$yukihookapi_core_release(YukiHookCallback.Param param) {
                if (before != null) before.accept(param);
            }

            @Override
            public void afterHookedMember$yukihookapi_core_release(YukiHookCallback.Param param) {
                if (after != null) after.accept(param);
            }
        };
    }

    public static Object hookMember(Member member, Object callback) {
        return YukiHookHelper.INSTANCE.hookMember$yukihookapi_core_release(member, (YukiHookCallback) callback);
    }

    public static Object invokeOriginal(Member member, Object instance, Object[] args) {
        return YukiHookHelper.INSTANCE.invokeOriginalMember$yukihookapi_core_release(member, instance, args);
    }

    public static void unhook(Object hookResult) {
        YukiHookResult result = (YukiHookResult) hookResult;
        result.getHookedMember().remove$yukihookapi_core_release();
    }

    public static Member member(Object param) {
        return ((YukiHookCallback.Param) param).getMember();
    }

    public static Object instance(Object param) {
        return ((YukiHookCallback.Param) param).getInstance();
    }

    public static Object[] args(Object param) {
        return ((YukiHookCallback.Param) param).getArgs();
    }

    public static Object result(Object param) {
        return ((YukiHookCallback.Param) param).getResult();
    }

    public static void setResult(Object param, Object result) {
        ((YukiHookCallback.Param) param).setResult(result);
    }

    public static Throwable throwable(Object param) {
        return ((YukiHookCallback.Param) param).getThrowable();
    }

    public static void setThrowable(Object param, Throwable throwable) {
        ((YukiHookCallback.Param) param).setThrowable(throwable);
    }
}
