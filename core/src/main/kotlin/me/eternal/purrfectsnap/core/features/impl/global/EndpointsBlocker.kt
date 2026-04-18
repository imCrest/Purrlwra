package cock.crest.purrfectsnap.lite.core.features.impl.global

import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.event.events.impl.NetworkApiRequestEvent
import cock.crest.purrfectsnap.lite.core.event.events.impl.UnaryCallEvent
import cock.crest.purrfectsnap.lite.core.util.dataBuilder
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import cock.crest.purrfectsnap.lite.mapper.impl.CallbackMapper
import cock.crest.purrfectsnap.lite.mapper.impl.PlatformClientAttestationMapper
import java.io.IOException
import java.lang.reflect.Method

class EndpointsBlocker : Feature("EndpointsBlocker") {
    @Volatile
    private var isInLoginSignup = false

    override fun init() {
        val bypassToggleEnabled = context.bridgeClient.getDebugProp("test_mode", "false") == "true"
        
        if (context.disablePlugin && !bypassToggleEnabled) {
            context.log.verbose("EndpointsBlocker: Plugin disabled and test_mode not enabled, skipping initialization")
            return
        }

        onNextActivityCreate { activity ->
            if (activity.javaClass.name.endsWith("LoginSignupActivity")) {
                isInLoginSignup = true
                runCatching { context.native.setInLoginSignup(true) }

                onNextActivityCreate {
                    isInLoginSignup = false
                    runCatching { context.native.setInLoginSignup(false) }
                }
            }
        }
        runCatching { context.native.setInLoginSignup(false) }

        if (bypassToggleEnabled) {
            context.native.setTestMode(true)
            val healthy = context.native.isEndpointBlockerHealthy(testMode = true)
            if (!healthy) {
                context.log.warn("EndpointsBlocker: native self-test failed, indicator set to inactive")
                context.log.warn("EndpointsBlocker: Possible causes - config not loaded or self-test logic failed")
            }
            context.inAppOverlay.showBypassStatusIndicator(healthy)
        } else {
            context.native.setTestMode(false)
        }

        context.event.subscribe(NetworkApiRequestEvent::class) { event ->
            val bypassToggleEnabled = context.bridgeClient.getDebugProp("test_mode", "false") == "true"
            if (!bypassToggleEnabled && context.disablePlugin) {
                return@subscribe
            }
            if (isInLoginSignup) return@subscribe

            val decision = context.native.evaluateNetworkRequest(event.url)
            if (decision.blocked) {
                event.canceled = true
            }
        }

        context.event.subscribe(UnaryCallEvent::class) { event ->
            val bypassToggleEnabled = context.bridgeClient.getDebugProp("test_mode", "false") == "true"
            if (!bypassToggleEnabled && context.disablePlugin) {
                return@subscribe
            }
            if (isInLoginSignup) return@subscribe
            
            val callOptions = event.adapter.arg<Any>(2).let { it.javaClass.getMethod("build").invoke(it) } ?: return@subscribe
            val hasAttestation = callOptions.getObjectField("mAttestation") != null
            val arg0 = event.adapter.arg<Any>(0).toString()

            val decision = context.native.evaluateEndpoint(event.uri, arg0, hasAttestation)
            if (decision.blocked) {
                event.canceled = true
                val eventHandler = event.adapter.arg<Any>(3)
                eventHandler.javaClass.methods.first { it.name == "onEvent" }.also { method ->
                    method.invoke(eventHandler, null, method.parameterTypes[0].dataBuilder {
                        set("mStatusCode", "CANCELLED")
                    })
                }
            }
        }

        context.androidContext.classLoader.apply {
            loadClass("com.snapchat.client.duplex.DuplexClient\$CppProxy").hook("registerHandler",
                HookStage.BEFORE) { param ->
                val bypassToggleEnabled = context.bridgeClient.getDebugProp("test_mode", "false") == "true"
                if (!bypassToggleEnabled && context.disablePlugin) {
                    return@hook
                }
                if (isInLoginSignup) return@hook
                
                val path = param.arg<String>(0)
                val blocked = context.native.shouldBlockDuplexClient(path)
                if (blocked) {
                    param.setResult(null)
                    return@hook
                }
            }
        }

        context.mappings.useMapper(CallbackMapper::class) {
            callbacks.getClass("AuthContextDelegate")?.hook("getAuthContext", HookStage.BEFORE) { param ->
                val bypassToggleEnabled = context.bridgeClient.getDebugProp("test_mode", "false") == "true"
                if (!bypassToggleEnabled && context.disablePlugin) {
                    return@hook
                }
                if (isInLoginSignup) return@hook
                
                val authContextRequest = param.arg<Any>(0)
                val requestPath = authContextRequest.getObjectField("mRequestPath").toString()
                val attestationRequired = authContextRequest.getObjectField("mAttestationRequired") == true
                val decision = context.native.evaluateAuthContext(requestPath, attestationRequired)
                if (decision.blocked) {
                    param.setResult(null)
                }
            } ?: error("AuthContextDelegate not found in mappings")
        }

        context.mappings.useMapper(PlatformClientAttestationMapper::class) {
            apiInvocationHandler.getAsClass()?.hook("invoke", HookStage.BEFORE) { param ->
                val bypassToggleEnabled = context.bridgeClient.getDebugProp("test_mode", "false") == "true"
                if (!bypassToggleEnabled && context.disablePlugin) {
                    return@hook
                }
                if (isInLoginSignup) return@hook
                
                val method = param.arg<Method>(1)
                val methodId = "${method.declaringClass.name}.${method.name}"
                val annotationBlob = method.annotations.joinToString("\n") { it.toString() }
                val decision = context.native.evaluateApiInvocation(methodId, annotationBlob)

                if (decision.blocked) {
                    if (method.returnType.name.endsWith("Single")) {
                        val errorSingle = method.returnType.methods.first {
                            java.lang.reflect.Modifier.isStatic(it.modifiers) && it.parameterCount == 1 && it.parameterTypes[0] == Throwable::class.java
                        }.invoke(null, IOException())
                        param.setResult(errorSingle)
                        return@hook
                    }
                    param.setResult(null)
                }
            } ?: context.log.warn("apiInvocationHandler not found in mappings")
        }
    }
}
