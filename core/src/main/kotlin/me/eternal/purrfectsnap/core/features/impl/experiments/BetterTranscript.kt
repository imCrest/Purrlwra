package cock.crest.purrfectsnap.lite.core.features.impl.experiments

import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoEditor
import cock.crest.purrfectsnap.lite.core.event.events.impl.BuildMessageEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.dataBuilder
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectFieldOrNull
import cock.crest.purrfectsnap.lite.core.util.ktx.setObjectField
import java.nio.ByteBuffer

class BetterTranscript: Feature("Better Transcript") {
    private val voiceML: Any by lazy {
        findClass("com.snapchat.client.voiceml.IVoiceMLSDK").getMethod("create").invoke(null) ?: error("Failed to create IVoiceMLSDK instance")
    }

    private fun createAsrConfig(): Any? {
        findClass("com.snapchat.client.voiceml.IConfigFactory").methods.first { it.name == "simpleAsrConfig" }.let { method ->
            return method.invoke(null, method.parameterTypes[0].dataBuilder {
                set("mSampleRate", 44100)
                set("mLanguageModel", "en")
                set("mUseCase", "VOICENOTESTRANSCRIPTION")
                set("mAppVersion", "voice note transcript")
                set("mUiLanguage", "en")
                set("mAuthType", "SNAPTOKEN")
                set("mEncoding", "AAC")
            })
        }
    }

    fun transcribe(audio: ByteBuffer): String? {
        val transcribeMethod = voiceML.javaClass.methods.first { it.name == "asrTranscribe" }
        val snapToken = context.database.getAccessTokens(context.database.myUserId)?.get("api-gateway") ?: error("Failed to get api-gateway token")

        return transcribeMethod.invoke(voiceML, snapToken, createAsrConfig(), audio)
            ?.let { asrResult ->
                asrResult.getObjectFieldOrNull("mTranscription")?.toString()
            }
    }

    override fun init() {
        if (context.config.experimental.betterTranscript.globalState != true) return

        onNextActivityCreate {
            val config = context.config.experimental.betterTranscript

            if (config.forceTranscription.get()) {
                context.event.subscribe(BuildMessageEvent::class, priority = 104) { event ->
                    if (event.message.messageContent?.contentType != ContentType.NOTE) return@subscribe
                    event.message.messageContent!!.content = ProtoEditor(event.message.messageContent!!.content!!).apply {
                        edit(6, 1) {
                            if (firstOrNull(3) == null) {
                                addString(3, context.getConfigLocale())
                            }
                        }
                    }.toByteArray()
                }
            }

            findClass("com.snapchat.client.voiceml.IVoiceMLSDK\$CppProxy").hook("asrTranscribe", HookStage.BEFORE) { param ->
                config.preferredTranscriptionLang.getNullable()?.takeIf {
                    it.isNotBlank()
                }?.trim()?.lowercase()?.let {
                    val asrConfig = param.arg<Any>(1)
                    asrConfig.getObjectFieldOrNull("mBaseConfig")?.apply {
                        setObjectField("mLanguageModel", it)
                        setObjectField("mUiLanguage", it)
                    }
                }
            }
        }
    }
}