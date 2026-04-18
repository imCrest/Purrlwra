package cock.crest.purrfectsnap.lite.core.features.impl.messaging


import kotlinx.coroutines.launch
import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoWriter
import cock.crest.purrfectsnap.lite.core.event.events.impl.BuildMessageEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.wrapper.impl.Message
import cock.crest.purrfectsnap.lite.core.wrapper.impl.getMessageText

class MessageTranslator : Feature("Instant Translation") {
    private val translationManager by lazy { TranslationManager(context) }
    private val config by lazy { context.config.messaging.instantTranslation }
    private var isPaused = false
    
    private fun shouldTranslateMessage(message: Message): Boolean {
        if (isPaused) return false
        val contentType = message.messageContent?.contentType ?: return false
        return contentType == ContentType.CHAT
    }
    
    private fun getMessageText(message: Message): String? {
        val content = message.messageContent?.content ?: return null
        return content.getMessageText(ContentType.CHAT)
    }
    
    private fun updateMessageText(message: Message, newText: String) {
        val messageContent = message.messageContent ?: return
        val newContent = ProtoWriter().apply {
            from(2) {
                addString(1, newText)
            }
        }.toByteArray()
        
        messageContent.content = newContent
    }
    
    private fun createTranslatedDisplayText(originalText: String, translatedText: String, position: String): String {
        return when (position) {
            "inline" -> "$originalText ($translatedText)"
            "above" -> "$translatedText\n$originalText"
            "below" -> "$originalText\n$translatedText"
            else -> "$originalText\n$translatedText"
        }
    }
    
    private fun createTranslatedText(originalText: String, translatedText: String, position: String): String {
        return when (position) {
            "inline" -> "$originalText ($translatedText)"
            "above" -> "$translatedText\n$originalText"
            "below" -> "$originalText\n$translatedText"
            else -> "$originalText\n$translatedText"
        }
    }
    
    override fun init() {
        if (!config.enabled.get()) return
        
        onNextActivityCreate {
            context.event.subscribe(BuildMessageEvent::class, priority = 10) { event ->
                val message = event.message
                if (!shouldTranslateMessage(message)) return@subscribe
                
                val originalText = getMessageText(message) ?: return@subscribe
                val sourceLang = config.sourceLanguage.get()
                val targetLang = config.targetLanguage.get()
                
                if (sourceLang == targetLang) return@subscribe
                
                context.coroutineScope.launch {
                    try {
                        val translatedText = translationManager.translateText(originalText, sourceLang, targetLang) ?: return@launch
                        
                        context.runOnUiThread {
                            val showOriginal = config.showOriginal.get()
                            val showTranslation = config.showTranslation.get()
                            
                            if (!showOriginal && showTranslation) {
                                updateMessageText(message, translatedText)
                            } else if (showOriginal && showTranslation) {
                                val position = config.translationPosition.get()
                                val displayText = createTranslatedDisplayText(originalText, translatedText, position)
                                updateMessageText(message, displayText)
                            }
                        }
                    } catch (e: Exception) {
                        context.log.error("Translation failed for message", e)
                        if (config.pauseOnError.get()) {
                            isPaused = true
                            context.log.warn("Translation paused due to errors")
                            context.shortToast(context.translation["toast_translation_service_unavailable"])
                        }
                    }
                }
            }
        }
        
        if (config.translateOnTap.get()) {
            hookMessageTap()
        }
        
        context.coroutineScope.launch {
            while (true) {
                kotlinx.coroutines.delay(300000) // 5 minutes
                if (isPaused) {
                    translationManager.resetBlockedStatus()
                    isPaused = false
                    context.log.info("Translation service re-enabled")
                }
            }
        }
    }
    
    private fun hookMessageTap() {
        findClass("com.snapchat.client.messaging.Message")?.hook("getText", HookStage.AFTER) { param ->
            val message = param.thisObject<Any>()
            val originalText = param.getResult() as? String ?: return@hook
            
            if (originalText.isBlank()) return@hook
            
            context.coroutineScope.launch {
                try {
                    val sourceLang = config.sourceLanguage.get()
                    val targetLang = config.targetLanguage.get()
                    
                    if (sourceLang == targetLang) return@launch
                    
                    val translatedText = translationManager.translateText(originalText, sourceLang, targetLang) ?: return@launch
                    
                    context.runOnUiThread {
                        val position = config.translationPosition.get()
                        val translatedText = createTranslatedText(originalText, translatedText, position)
                        param.setResult(translatedText)
                    }
                } catch (e: Exception) {
                    context.log.error("Translation failed for tap", e)
                }
            }
        }
    }
}


