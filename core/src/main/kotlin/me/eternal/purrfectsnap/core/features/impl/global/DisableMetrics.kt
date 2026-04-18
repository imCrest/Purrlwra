package cock.crest.purrfectsnap.lite.core.features.impl.global

import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoReader
import cock.crest.purrfectsnap.lite.core.event.events.impl.NetworkApiRequestEvent
import cock.crest.purrfectsnap.lite.core.event.events.impl.UnaryCallEvent
import cock.crest.purrfectsnap.lite.core.features.Feature

class DisableMetrics : Feature("DisableMetrics") {
    override fun init() {
        if (!context.config.global.disableMetrics.get() && context.config.global.performanceMode.profile.getNullable() == null) return

        context.event.subscribe(NetworkApiRequestEvent::class) { param ->
            val url = param.url
            if (url.contains("app-analytics") || url.endsWith("metrics") || url.contains("streaming-collector")) {
                param.canceled = true
            }
        }

        context.event.subscribe(UnaryCallEvent::class) { event ->
            if (event.uri.startsWith("/snap.security.IntegritySyncService/")) {
                event.canceled = true
            }
            if (event.uri.startsWith("/snapchat.cdp.cof.CircumstancesService/")) {
                if (ProtoReader(event.buffer).getVarInt(21) == 1L) return@subscribe
                event.canceled = true
            }
        }
    }
}
