package cock.crest.purrfectsnap.lite.core.features.impl.tweaks

import cock.crest.purrfectsnap.lite.core.event.events.impl.NetworkApiRequestEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook

class RefreshFriendSuggestions : Feature("Refresh Friend Suggestions") {
    override fun init() {
        listOf("Y26", "y26").forEach { className ->
            listOf("m34803a", "mo81d").forEach { methodName ->
                runCatching {
                    findClass(className).hook(methodName, HookStage.AFTER) { param ->
                        val result = param.getResult()
                        val updated = (result as? Map<*, *>)?.toMutableMap() ?: return@hook
                        updated["_t"] = System.currentTimeMillis().toString()
                        updated["limit"] = "100"
                        param.setResult(updated)
                    }
                }.onFailure {}
            }
        }

        listOf("Hl3", "HL3", "nh3").forEach { className ->
            listOf("m13680e2", "m58660d3").forEach { methodName ->
                runCatching {
                    findClass(className).hook(methodName, HookStage.BEFORE) { param ->
                        if (param.arg<Int>(1) == 10) {
                            param.setArg(1, 100)
                        }
                    }
                }.onFailure {}
            }
        }

        context.event.subscribe(NetworkApiRequestEvent::class) { event ->
            if (event.url.contains("suggest_friend")) {
                val separator = if (event.url.contains("?")) "&" else "?"
                event.url += "${separator}_t=${System.currentTimeMillis()}&limit=100"
            }
        }
    }
}

