package cock.crest.purrfectsnap.lite.core.features.impl.messaging

import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.common.data.MessagingRuleType
import cock.crest.purrfectsnap.lite.common.data.MessageState
import cock.crest.purrfectsnap.lite.core.event.events.impl.BuildMessageEvent
import cock.crest.purrfectsnap.lite.core.event.events.impl.ConversationUpdateEvent
import cock.crest.purrfectsnap.lite.core.features.MessagingRuleFeature
import cock.crest.purrfectsnap.lite.core.features.impl.spying.HalfSwipeNotifier
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import cock.crest.purrfectsnap.lite.core.util.hook.hookConstructor
import cock.crest.purrfectsnap.lite.core.wrapper.impl.Message
import cock.crest.purrfectsnap.lite.core.wrapper.impl.SnapUUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import cock.crest.purrfectsnap.lite.core.wrapper.impl.MessageContent
import cock.crest.purrfectsnap.lite.core.wrapper.impl.getMessageText
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.LinkedList
import java.net.URL
import java.net.HttpURLConnection
import java.io.OutputStreamWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.IOException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

class AutoReply : MessagingRuleFeature("Auto Reply", MessagingRuleType.AUTO_REPLY) {
    companion object {
        private const val MAX_PROCESSED_MESSAGES = 5000
        private const val MAX_MESSAGE_INDICES = 500
        private const val CLEANUP_INTERVAL_MS = 180000L
        private const val COOLDOWN_CLEANUP_THRESHOLD_MS = 24 * 60 * 60 * 1000L
        private const val DEFAULT_FALLBACK_MESSAGE = "I'm currently away and will respond soon!"
        private const val MAX_CONVERSATION_HISTORY = 200
        private const val MAX_RECENT_RESPONSES = 20
        private const val RESPONSE_SIMILARITY_THRESHOLD = 0.75
    }

    private val processedMessages = CopyOnWriteArraySet<Long>()
    private val conversationCooldowns = ConcurrentHashMap<String, Long>()
    private val activeConversations = CopyOnWriteArraySet<String>()
    private val messageIndices = ConcurrentHashMap<String, Int>()
    private val lastCleanup = AtomicLong(System.currentTimeMillis())
    
    private val cooldownMutex = Mutex()
    private val indicesMutex = Mutex()
    private val messageProcessingMutex = Mutex()
    
    private val gson = Gson()
    private val messagingFeature by lazy { context.feature(Messaging::class) }
    private val httpClient by lazy { OkHttpClient.Builder().build() }
    private val historyMutex = Mutex()
    private val responseMutex = Mutex()
    private val conversationHistory = ConcurrentHashMap<String, LinkedList<ConversationMessage>>()
    private val recentResponses = ConcurrentHashMap<String, LinkedList<String>>()
    private val responseVariations = ConcurrentHashMap<String, Int>()
    private val conversationPersonality = ConcurrentHashMap<String, String>()
    
    private fun detectStoryContentType(messageContent: MessageContent?): ContentType? {
        if (messageContent?.content == null) return null
        
        try {
            val protoReader = cock.crest.purrfectsnap.lite.common.util.protobuf.ProtoReader(messageContent.content!!)
            
            if (protoReader.contains(7)) {
                return ContentType.STORY_REPLY
            }
            
            if (protoReader.contains(5)) {
                protoReader.followPath(5)?.let { share ->
                    if (share.contains(16)) {
                        return ContentType.SHARE
                    }
                }
            }
            
            if (protoReader.contains(3)) {
                protoReader.followPath(3)?.let { external ->
                    if (external.contains(7) || external.contains(5)) {
                        val storyText = protoReader.getString(7, 11, 1)
                        if (storyText != null) {
                            return ContentType.STORY_REPLY
                        }
                    }
                }
            }
            
            return null
        } catch (e: Exception) {
            context.log.error("Error detecting story content type", e)
            return null
        }
    }
    
    private suspend fun canSendAutoReply(
        conversationId: String,
        messageTime: Long? = null,
        isHalfSwipe: Boolean = false
    ): Boolean {
        val config = context.config.messaging.autoReply
        val currentTime = System.currentTimeMillis()
        
        try {
            if (config.globalState != true) {
                return false
            }
            
            if (!canUseRule(conversationId)) {
                return false
            }
            
            if (activeConversations.contains(conversationId)) {
                return false
            }
            
            val cooldownMs = config.cooldownSeconds.get() * 1000L
            cooldownMutex.withLock {
                val lastReplyTime = conversationCooldowns[conversationId] ?: 0
                if (currentTime - lastReplyTime < cooldownMs) {
                    return false
                }
            }
            
            messageTime?.let { msgTime ->
                val ageThresholdMs = config.messageAgeThreshold.get() * 1000L
                if (currentTime - msgTime > ageThresholdMs) {
                    return false
                }
            }
            
            if (isHalfSwipe && !config.autoTriggerConfig.autoReplyContentTypes.get().contains("half_swipes")) {
                return false
            }
            
            return true
            
        } catch (e: Exception) {
            context.log.error("Error in auto-reply validation", e)
            return false
        }
    }
    
    private fun shouldReplyToContentType(contentType: ContentType?): Boolean {
        val config = context.config.messaging.autoReply
        val selectedContentTypes = config.autoTriggerConfig.autoReplyContentTypes.get()
        
        return when (contentType) {
            ContentType.CHAT -> selectedContentTypes.contains("chat_messages")
            ContentType.SNAP -> selectedContentTypes.contains("snap_messages")
            ContentType.EXTERNAL_MEDIA -> selectedContentTypes.contains("external_media_messages")
            ContentType.STICKER -> selectedContentTypes.contains("sticker_messages")
            ContentType.TINY_SNAP -> selectedContentTypes.contains("tiny_snap_messages")
            ContentType.MAP_REACTION -> selectedContentTypes.contains("map_reaction_messages")
            ContentType.NOTE -> selectedContentTypes.contains("voice_note_messages")
            ContentType.SHARE -> selectedContentTypes.contains("story_share_messages")
            ContentType.STORY_REPLY -> selectedContentTypes.contains("story_reply_messages")
            null, 
            ContentType.STATUS,
            ContentType.UNKNOWN -> {
                if (contentType == ContentType.STATUS) {
                    false
                } else {
                    selectedContentTypes.contains("chat_messages")
                }
            }
            else -> selectedContentTypes.contains("chat_messages")
        }
    }
    
    private fun parseMessageList(jsonString: String): List<String> {
        val input = jsonString.trim()
        if (input.isBlank()) return listOf(DEFAULT_FALLBACK_MESSAGE)

        return try {
            if (input.startsWith("[")) {
                val type = object : TypeToken<List<String>>() {}.type
                val parsed = gson.fromJson<List<String>>(input, type) ?: emptyList()
                val filtered = parsed.filter { it.isNotBlank() }
                if (filtered.isEmpty()) listOf(DEFAULT_FALLBACK_MESSAGE) else filtered
            } else {
                val single = runCatching { gson.fromJson(input, String::class.java) }.getOrElse { input }.trim()
                if (single.isNotBlank()) listOf(single) else listOf(DEFAULT_FALLBACK_MESSAGE)
            }
        } catch (e: Exception) {
            context.log.error("Failed to parse message list: $jsonString", e)
            listOf(DEFAULT_FALLBACK_MESSAGE)
        }
    }
    
    private suspend fun getNextMessage(messageList: List<String>, conversationId: String, contentType: String): String {
        if (messageList.isEmpty()) {
            return DEFAULT_FALLBACK_MESSAGE
        }
        
        if (messageList.size == 1) {
            return messageList[0]
        }
        
        val key = "${conversationId}_${contentType}"
        
        return indicesMutex.withLock {
            if (messageIndices.size > MAX_MESSAGE_INDICES) {
                messageIndices.clear()
            }
            
            val currentIndex = messageIndices.getOrDefault(key, 0)
            val nextIndex = (currentIndex + 1) % messageList.size
            
            messageIndices[key] = nextIndex
            messageList[currentIndex]
        }
    }
    
    override fun init() {
        if (context.config.messaging.autoReply.globalState != true) {
            return
        }
        
        if (getRuleState() == null) return
        
        context.log.info("AutoReply - Initialized with content types: ${context.config.messaging.autoReply.autoTriggerConfig.autoReplyContentTypes.get()}")
        
        if (context.config.messaging.autoReply.allowRunningInBackground.get()) {
            findClass("com.snapchat.client.duplex.DuplexClient\$CppProxy").apply {
                hook("appStateChanged", HookStage.BEFORE) { param ->
                    if (param.arg<Any>(0).toString() == "INACTIVE") param.setResult(null)
                }
                hookConstructor(HookStage.AFTER) { param ->
                    methods.first { it.name == "appStateChanged" }.let { method ->
                        method.invoke(param.thisObject(), method.parameterTypes[0].enumConstants!!.first { it.toString() == "ACTIVE" })
                    }
                }
            }
        }
        
        context.event.subscribe(ConversationUpdateEvent::class, priority = 100) { event ->
            val conversationId = event.conversationId
            val currentTime = System.currentTimeMillis()
            
            try {
                updateActiveConversations(conversationId)
                processNewMessages(event, currentTime)
                triggerCleanupIfNeeded(currentTime)
            } catch (e: Exception) {
                context.log.error("Error processing conversation update for auto-reply", e)
            }
        }
        
        context.event.subscribe(BuildMessageEvent::class, priority = 100) { event ->
            val currentTime = System.currentTimeMillis()
            
            try {
                if (event.message.messageState != MessageState.COMMITTED) return@subscribe
                if (event.message.senderId?.toString() == context.database.myUserId) return@subscribe
                
                val conversationId = event.message.messageDescriptor?.conversationId?.toString() ?: return@subscribe
                val messageId = event.message.messageDescriptor?.messageId ?: return@subscribe
                val senderId = event.message.senderId?.toString() ?: return@subscribe
                val messageTime = event.message.messageMetadata?.createdAt
                val contentType = event.message.messageContent?.contentType
                
                val detectedContentType = detectStoryContentType(event.message.messageContent) ?: contentType
                
                if (!shouldReplyToContentType(detectedContentType)) {
                    return@subscribe
                }
                
                context.coroutineScope.launch(Dispatchers.IO) {
                    try {
                        messageProcessingMutex.withLock {
                            if (processedMessages.contains(messageId)) return@launch
                            
                            if (!canSendAutoReply(conversationId, messageTime)) return@launch
                            
                            if (processedMessages.size > MAX_PROCESSED_MESSAGES) {
                                processedMessages.clear()
                            }
                            processedMessages.add(messageId)
                        }
                        
                        val aiCfg = context.config.messaging.autoReply.aiConfig
                        if (aiCfg.enableAiReplies.get() && aiCfg.aiUseConversationHistory.get()) {
                            val messageContent = extractMessageContent(event.message, detectedContentType)
                            addToConversationHistory(conversationId, messageContent, isFromMe = false, detectedContentType)
                        }
                        
                        val replyText = generateReply(event.message, senderId, conversationId, detectedContentType)
                        if (replyText.isNotEmpty()) {
                            sendAutoReply(conversationId, replyText)
                            if (aiCfg.enableAiReplies.get() && aiCfg.aiUseConversationHistory.get()) {
                                addToConversationHistory(conversationId, replyText, isFromMe = true, ContentType.CHAT)
                            }
                            updateCooldown(conversationId, currentTime)
                        }
                    } catch (e: Exception) {
                        context.log.error("Error processing BuildMessageEvent for auto-reply", e)
                    }
                }
            } catch (e: Exception) {
                context.log.error("Error handling BuildMessageEvent for auto-reply", e)
            }
        }
        
        context.feature(HalfSwipeNotifier::class).addOnHalfSwipeListener { conversationId, userId, duration ->
            context.coroutineScope.launch(Dispatchers.IO) {
                try {
                    handleHalfSwipe(conversationId, userId, duration)
                } catch (e: Exception) {
                    context.log.error("Error handling half-swipe auto-reply", e)
                }
            }
        }
        
        context.coroutineScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    delay(CLEANUP_INTERVAL_MS)
                    performCleanup()
                } catch (e: Exception) {
                    context.log.error("Error in auto-reply cleanup task", e)
                }
            }
        }
    }
    
    private fun updateActiveConversations(conversationId: String) {
        val openedConversationId = messagingFeature.openedConversationUUID?.toString()
        if (openedConversationId == conversationId) {
            activeConversations.add(conversationId)
        } else {
            activeConversations.remove(conversationId)
        }
    }
    
    private fun processNewMessages(event: ConversationUpdateEvent, currentTime: Long) {
        val conversationId = event.conversationId
        val myUserId = context.database.myUserId
        
        context.coroutineScope.launch(Dispatchers.IO) {
            try {
                if (!canSendAutoReply(conversationId)) {
                    return@launch
                }
                
                val sortedMessages = event.messages.sortedByDescending { it.messageMetadata?.createdAt ?: 0L }
                
                for (message in sortedMessages) {
                    try {
                        val messageId = message.messageDescriptor?.messageId ?: continue
                        val senderId = message.senderId?.toString()
                        val messageTime = message.messageMetadata?.createdAt
                        val messageContent = message.messageContent
                        val contentType = messageContent?.contentType
                        
                        if (senderId == myUserId) {
                            continue
                        }
                        
                        if (message.messageState != MessageState.COMMITTED) {
                            continue
                        }
                        
                        if (processedMessages.contains(messageId)) {
                            continue
                        }
                        
                        val detectedContentType = detectStoryContentType(messageContent) ?: contentType
                        
                        if (!shouldReplyToContentType(detectedContentType)) {
                            continue
                        }
                        
                        if (!canSendAutoReply(conversationId, messageTime)) {
                            continue
                        }
                        
                        processMessage(conversationId, message, currentTime, detectedContentType)
                        break
                        
                    } catch (e: Exception) {
                        context.log.error("Error processing individual message for auto-reply", e)
                        continue
                    }
                }
            } catch (e: Exception) {
                context.log.error("Error processing new messages for auto-reply", e)
            }
        }
    }
    
    private suspend fun processMessage(conversationId: String, message: Message, currentTime: Long, detectedContentType: ContentType? = null) {
        val messageId = message.messageDescriptor?.messageId ?: return
        val senderId = message.senderId?.toString() ?: return
        
        try {
            messageProcessingMutex.withLock {
                if (processedMessages.contains(messageId)) return
                
                if (processedMessages.size > MAX_PROCESSED_MESSAGES) {
                    processedMessages.clear()
                }
                processedMessages.add(messageId)
            }
            
            val aiCfg = context.config.messaging.autoReply.aiConfig
            if (aiCfg.enableAiReplies.get() && aiCfg.aiUseConversationHistory.get()) {
                val messageContent = extractMessageContent(message, detectedContentType)
                addToConversationHistory(conversationId, messageContent, isFromMe = false, detectedContentType)
            }
            
            val replyText = generateReply(message, senderId, conversationId, detectedContentType)
            if (replyText.isNotEmpty()) {
                sendAutoReply(conversationId, replyText)
                if (aiCfg.enableAiReplies.get() && aiCfg.aiUseConversationHistory.get()) {
                    addToConversationHistory(conversationId, replyText, isFromMe = true, ContentType.CHAT)
                }
                updateCooldown(conversationId, currentTime)
            }
        } catch (e: Exception) {
            context.log.error("Error processing message for auto-reply", e)
        }
    }
    
    private suspend fun generateReply(message: Message, senderId: String, conversationId: String, detectedContentType: ContentType? = null): String {
        val config = context.config.messaging.autoReply
        
        try {
            val messageContent = message.messageContent
            val contentType = detectedContentType ?: messageContent?.contentType
            

            val messageListJson = when (contentType) {
                ContentType.CHAT -> config.autoTriggerConfig.chatMessages.get()
                ContentType.SNAP -> config.autoTriggerConfig.snapMessages.get()
                ContentType.EXTERNAL_MEDIA -> config.autoTriggerConfig.externalMediaMessages.get()
                ContentType.STICKER -> config.autoTriggerConfig.stickerMessages.get()
                ContentType.TINY_SNAP -> config.autoTriggerConfig.tinySnapMessages.get()
                ContentType.MAP_REACTION -> config.autoTriggerConfig.mapReactionMessages.get()
                ContentType.NOTE -> config.autoTriggerConfig.voiceNoteMessages.get()
                ContentType.SHARE -> config.autoTriggerConfig.storyShareMessages.get()
                ContentType.STORY_REPLY -> config.autoTriggerConfig.storyReplyMessages.get()
                else -> config.autoTriggerConfig.chatMessages.get()
            }
            val messageList = parseMessageList(messageListJson)
            val contentTypeName = contentType?.name ?: "CHAT"
            val baseMessage = getNextMessage(messageList, conversationId, contentTypeName)
            val withGreeting = if (config.autoTriggerConfig.friendSpecificGreeting.get()) {
                val friendInfo = context.database.getFriendInfo(senderId)
                val friendName = friendInfo?.displayName ?: friendInfo?.mutableUsername ?: "Friend"
                val greeting = config.autoTriggerConfig.friendGreeting.get().takeIf { it.isNotBlank() } ?: "Hey"
                "$greeting $friendName! $baseMessage"
            } else {
                baseMessage
            }

            val aiCfg = config.aiConfig
            if (aiCfg.enableAiReplies.get()) {
                return generateAiReply(message, senderId, conversationId, detectedContentType, withGreeting)
            }
            return withGreeting
        } catch (e: Exception) {
            context.log.error("Error generating auto-reply message", e)
            return DEFAULT_FALLBACK_MESSAGE
        }
    }

    private suspend fun generateAiReply(message: Message, senderId: String, conversationId: String, detectedContentType: ContentType?, fallback: String): String {
        val ai = context.config.messaging.autoReply.aiConfig
        val provider = ai.aiProvider.get()
        val apiKey = ai.aiApiKey.get().trim()
        val model = when (provider) {
            "openrouter" -> ai.aiModel.get().ifBlank { "deepseek/deepseek-r1-0528:free" }
            "deepseek" -> ai.aiModel.get().ifBlank { "deepseek-chat" }
            "openai" -> ai.aiModel.get().ifBlank { "gpt-4o-mini" }
            "gemini" -> ai.aiModel.get().ifBlank { "gemini-2.0-flash" }
            else -> ai.aiModel.get().ifBlank { "gemini-2.0-flash" }
        }
        val timeoutSec = ai.aiRequestTimeout.get()
        val maxRetries = ai.aiRetryAttempts.get()
        val temp = (ai.aiTemperature.get().toDoubleOrNull() ?: 0.7).coerceIn(0.0, 2.0)
        val maxTokens = ai.aiMaxTokens.get()

        if (apiKey.isBlank()) {
            context.log.warn("AI reply skipped: missing API key for $provider")
            return if (ai.aiFallbackToTemplate.get()) fallback else ""
        }

        val messages = if (ai.aiUseConversationHistory.get()) {
            buildAIMessages(conversationId, senderId, message, detectedContentType)
        } else {
            val systemPrompt = buildSystemPrompt(senderId, detectedContentType, context.config.messaging.autoReply)
            val currentContent = extractMessageContent(message, detectedContentType)
            val formattedContent = formatMessageForAI(currentContent, detectedContentType)
            listOf(
                AIMessage("system", systemPrompt),
                AIMessage("user", formattedContent)
            )
        }

        var lastError: Throwable? = null
        repeat(maxRetries + 1) { attempt ->
            val result = runCatching {
                performAiRequest(provider, model, apiKey, messages, timeoutSec, temp, maxTokens)
            }.getOrElse { err ->
                lastError = err
                context.log.warn("AI reply attempt ${attempt + 1} failed for provider $provider model $model timeout ${timeoutSec}s conversation $conversationId: ${err.message ?: err.localizedMessage}")
                if (attempt < maxRetries) {
                    delay(1000L * (attempt + 1))
                    null
                } else {
                    null
                }
            }
            if (result != null) {
                val response = result.ifBlank { if (ai.aiFallbackToTemplate.get()) fallback else "" }
                if (response.isNotEmpty()) {
                    return response
                }
            }
        }

        context.log.warn("AI reply failed after ${maxRetries + 1} attempts: ${lastError?.message}")
        return if (ai.aiFallbackToTemplate.get()) fallback else ""
    }

    private suspend fun handleHalfSwipe(conversationId: String, userId: String, duration: Long) {
        val currentTime = System.currentTimeMillis()
        
        try {
            if (!canSendAutoReply(conversationId, currentTime, isHalfSwipe = true)) {
                return
            }
            
            val config = context.config.messaging.autoReply
            val halfSwipeMessages = parseMessageList(config.autoTriggerConfig.halfSwipeMessages.get())
            val baseMessage = getNextMessage(halfSwipeMessages, conversationId, "HALF_SWIPE")
            
            val withGreeting = if (config.autoTriggerConfig.friendSpecificGreeting.get()) {
                val friendInfo = context.database.getFriendInfo(userId)
                val friendName = friendInfo?.displayName ?: friendInfo?.mutableUsername ?: "Friend"
                val greeting = config.autoTriggerConfig.friendGreeting.get().takeIf { it.isNotBlank() } ?: "Hey"
                "$greeting $friendName! $baseMessage"
            } else {
                baseMessage
            }
            
            val aiCfg = config.aiConfig
            val replyText = if (aiCfg.enableAiReplies.get()) {
                generateAiReplyForHalfSwipe(userId, conversationId, withGreeting)
            } else {
                withGreeting
            }
            
            if (replyText.isNotEmpty()) {
                sendAutoReply(conversationId, replyText)
                if (aiCfg.enableAiReplies.get() && aiCfg.aiUseConversationHistory.get()) {
                    addToConversationHistory(conversationId, replyText, isFromMe = true, ContentType.CHAT)
                }
                updateCooldown(conversationId, currentTime)
            }
            
        } catch (e: Exception) {
            context.log.error("Error handling half-swipe auto-reply", e)
        }
    }

    private suspend fun generateAiReplyForHalfSwipe(senderId: String, conversationId: String, fallback: String): String {
        val ai = context.config.messaging.autoReply.aiConfig
        val provider = ai.aiProvider.get()
        val apiKey = ai.aiApiKey.get().trim()
        val model = when (provider) {
            "openrouter" -> ai.aiModel.get().ifBlank { "deepseek/deepseek-r1-0528:free" }
            "deepseek" -> ai.aiModel.get().ifBlank { "deepseek-chat" }
            "openai" -> ai.aiModel.get().ifBlank { "gpt-4o-mini" }
            "gemini" -> ai.aiModel.get().ifBlank { "gemini-2.0-flash" }
            else -> ai.aiModel.get().ifBlank { "gemini-2.0-flash" }
        }
        val timeoutSec = ai.aiRequestTimeout.get()
        val maxRetries = ai.aiRetryAttempts.get()
        val temp = (ai.aiTemperature.get().toDoubleOrNull() ?: 0.7).coerceIn(0.0, 2.0)
        val maxTokens = ai.aiMaxTokens.get()

        if (apiKey.isBlank()) {
            context.log.warn("AI reply skipped: missing API key for $provider")
            return if (ai.aiFallbackToTemplate.get()) fallback else ""
        }

        val systemPrompt = buildSystemPrompt(senderId, null, context.config.messaging.autoReply, isHalfSwipe = true)
        val userPrompt = "They half-swiped on your conversation (peeked at your chat without opening it)."
        
        val messages = if (ai.aiUseConversationHistory.get()) {
            val messagesList = mutableListOf<AIMessage>()
            messagesList.add(AIMessage("system", systemPrompt))
            historyMutex.withLock {
                val history = conversationHistory[conversationId] ?: LinkedList()
                val contextLength = ai.aiContextLength.get()
                val recentMessages = history.takeLast(contextLength)
                for (historyMessage in recentMessages) {
                    val role = if (historyMessage.isFromMe) "assistant" else "user"
                    val messageContent = formatMessageForAI(historyMessage.content, historyMessage.contentType)
                    messagesList.add(AIMessage(role, messageContent))
                }
            }
            messagesList.add(AIMessage("user", userPrompt))
            messagesList
        } else {
            listOf(
                AIMessage("system", systemPrompt),
                AIMessage("user", userPrompt)
            )
        }

        var lastError: Throwable? = null
        repeat(maxRetries + 1) { attempt ->
            val result = runCatching {
                performAiRequest(provider, model, apiKey, messages, timeoutSec, temp, maxTokens)
            }.getOrElse { err ->
                lastError = err
                context.log.warn("AI half-swipe reply attempt ${attempt + 1} failed for provider $provider model $model timeout ${timeoutSec}s conversation $conversationId: ${err.message ?: err.localizedMessage}")
                if (attempt < maxRetries) {
                    delay(1000L * (attempt + 1))
                    null
                } else {
                    null
                }
            }
            if (result != null) {
                val response = result.ifBlank { if (ai.aiFallbackToTemplate.get()) fallback else "" }
                if (response.isNotEmpty()) {
                    return response
                }
            }
        }

        context.log.warn("AI reply failed after ${maxRetries + 1} attempts: ${lastError?.message}")
        return if (ai.aiFallbackToTemplate.get()) fallback else ""
    }

    private suspend fun performAiRequest(
        provider: String,
        model: String,
        apiKey: String,
        messages: List<AIMessage>,
        timeoutSec: Int,
        temp: Double,
        maxTokens: Int
    ): String = withTimeout(timeoutSec * 1000L) {
        withContext(Dispatchers.IO) {
            when (provider) {
                "openrouter", "openai", "deepseek" -> {
                    val payload = JsonObject().apply {
                        addProperty("model", model)
                        add("messages", JsonArray().apply {
                            messages.forEach { msg ->
                                add(JsonObject().apply {
                                    addProperty("role", msg.role)
                                    addProperty("content", msg.content)
                                })
                            }
                        })
                        if (maxTokens > 0) addProperty("max_tokens", maxTokens)
                        addProperty("temperature", temp)
                    }

                    val apiUrl = when (provider) {
                        "openrouter" -> "https://openrouter.ai/api/v1/chat/completions"
                        "openai" -> "https://api.openai.com/v1/chat/completions"
                        else -> "https://api.deepseek.com/v1/chat/completions"
                    }

                    val reqBuilder = Request.Builder()
                        .url(apiUrl)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")

                    if (provider == "openrouter") {
                        reqBuilder
                            .addHeader("HTTP-Referer", "https://purrfectsnap.app")
                            .addHeader("X-Title", "PurrfectSnap")
                    }

                    val request = reqBuilder
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    httpClient.newCall(request).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            throw Throwable("AI error ${resp.code}: ${body.take(200)}")
                        }

                        JsonParser.parseString(body)
                            .asJsonObject
                            .getAsJsonArray("choices")
                            ?.firstOrNull()?.asJsonObject
                            ?.getAsJsonObject("message")
                            ?.get("content")
                            ?.asString
                            ?.trim()
                            .orEmpty()
                    }
                }
                "gemini" -> {
                    val payload = JsonObject().apply {
                        val contents = JsonArray()
                        val systemParts = JsonArray()
                        
                        val mergedMessages = mutableListOf<Pair<String, StringBuilder>>()
                        messages.forEach { msg ->
                            if (msg.role == "system") {
                                systemParts.add(JsonObject().apply { addProperty("text", msg.content) })
                            } else {
                                val geminiRole = if (msg.role == "assistant") "model" else "user"
                                if (mergedMessages.isNotEmpty() && mergedMessages.last().first == geminiRole) {
                                    mergedMessages.last().second.append("\n").append(msg.content)
                                } else {
                                    mergedMessages.add(geminiRole to StringBuilder(msg.content))
                                }
                            }
                        }
                        
                        mergedMessages.forEach { (role, content) ->
                            if (contents.size() == 0 && role == "model") return@forEach // Gemini contents turn must start with user
                            contents.add(JsonObject().apply {
                                addProperty("role", role)
                                add("parts", JsonArray().apply {
                                    add(JsonObject().apply { addProperty("text", content.toString()) })
                                })
                            })
                        }
                        
                        add("contents", contents)
                        if (systemParts.size() > 0) {
                            add("system_instruction", JsonObject().apply {
                                add("parts", systemParts)
                            })
                        }
                        
                        add("generationConfig", JsonObject().apply {
                            addProperty("temperature", temp)
                            if (maxTokens > 0) addProperty("maxOutputTokens", maxTokens)
                        })
                    }

                    val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                    
                    val request = Request.Builder()
                        .url(apiUrl)
                        .addHeader("Content-Type", "application/json")
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    httpClient.newCall(request).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            throw Throwable("AI error ${resp.code}: ${body.take(200)}")
                        }

                        JsonParser.parseString(body)
                            .asJsonObject
                            .getAsJsonArray("candidates")
                            ?.firstOrNull()?.asJsonObject
                            ?.getAsJsonObject("content")
                            ?.getAsJsonArray("parts")
                            ?.firstOrNull()?.asJsonObject
                            ?.get("text")
                            ?.asString
                            ?.trim()
                            .orEmpty()
                    }
                }
                else -> throw IllegalArgumentException("Unsupported AI provider: $provider")
            }
        }
    }


    // === AI helper structures ===
    private data class AIMessage(val role: String, val content: String)
    private data class ConversationMessage(
        val content: String,
        val isFromMe: Boolean,
        val timestamp: Long,
        val contentType: ContentType?
    )
    private data class AIRequest(val model: String, val messages: List<AIMessage>, val temperature: Double = 0.7)
    private data class AIResponse(val choices: List<AIChoice>)
    private data class AIChoice(val message: AIMessage)

    private suspend fun buildAIMessages(conversationId: String, senderId: String, currentMessage: Message, contentType: ContentType?): List<AIMessage> {
        val config = context.config.messaging.autoReply
        val messages = mutableListOf<AIMessage>()
        val systemPrompt = buildSystemPrompt(senderId, contentType, config)
        messages.add(AIMessage("system", systemPrompt))
        if (config.aiConfig.aiUseConversationHistory.get()) {
            historyMutex.withLock {
                val history = conversationHistory[conversationId] ?: LinkedList()
                val contextLength = config.aiConfig.aiContextLength.get()
                val recentMessages = history.takeLast(contextLength)
                for (historyMessage in recentMessages) {
                    val role = if (historyMessage.isFromMe) "assistant" else "user"
                    val messageContent = formatMessageForAI(historyMessage.content, historyMessage.contentType)
                    messages.add(AIMessage(role, messageContent))
                }
            }
        }
        val currentContent = extractMessageContent(currentMessage, contentType)
        if (currentContent.isNotEmpty()) messages.add(AIMessage("user", formatMessageForAI(currentContent, contentType)))
        return messages
    }

    private fun formatMessageForAI(content: String, contentType: ContentType?): String = when (contentType) {
        ContentType.SNAP -> "sent a snap: $content"
        ContentType.CHAT -> content
        ContentType.EXTERNAL_MEDIA -> "shared media: $content"
        ContentType.STICKER -> "sent a sticker: $content"
        ContentType.NOTE -> "sent a note: $content"
        ContentType.LOCATION -> "shared location: $content"
        ContentType.LIVE_LOCATION_SHARE -> "shared live location: $content"
        ContentType.FAMILY_CENTER_INVITE -> "family center message: $content"
        else -> content
    }

    private suspend fun buildSystemPrompt(senderId: String, contentType: ContentType?, config: cock.crest.purrfectsnap.lite.common.config.impl.MessagingTweaks.AutoReplyConfig, isHalfSwipe: Boolean = false): String {
        val responseLanguage = config.aiConfig.aiResponseLanguage.get()
        val provider = config.aiConfig.aiProvider.get()
        val strict = provider != "deepseek"
        val languageInstruction = when (responseLanguage) {
            "auto" -> if (strict) "Detect the incoming message language and reply strictly in that language without mixing other languages." else "Always respond in the same language as the message you received."
            "en" -> if (strict) "Respond only in English with no words from other languages." else "Always respond in English."
            "es" -> if (strict) "Respond only in Spanish with no words from other languages." else "Always respond in Spanish."
            "fr" -> if (strict) "Respond only in French with no words from other languages." else "Always respond in French."
            "de" -> if (strict) "Respond only in German with no words from other languages." else "Always respond in German."
            "it" -> if (strict) "Respond only in Italian with no words from other languages." else "Always respond in Italian."
            "pt" -> if (strict) "Respond only in Portuguese with no words from other languages." else "Always respond in Portuguese."
            "ru" -> if (strict) "Respond only in Russian with no words from other languages." else "Always respond in Russian."
            "ja" -> if (strict) "Respond only in Japanese with no words from other languages." else "Always respond in Japanese."
            "ko" -> if (strict) "Respond only in Korean with no words from other languages." else "Always respond in Korean."
            "zh" -> if (strict) "Respond only in Chinese with no words from other languages." else "Always respond in Chinese."
            "ar" -> if (strict) "Respond only in Arabic using UAE (Emirati) and KSA (Saudi) dialect expressions with no words from other languages." else "Always respond in Arabic using UAE (Emirati) and KSA (Saudi) dialect and expressions. Use Gulf Arabic vocabulary and phrases common in the United Arab Emirates and Saudi Arabia."
            "hi" -> if (strict) "Respond only in Hindi with no words from other languages." else "Always respond in Hindi."
            "tr" -> if (strict) "Respond only in Turkish with no words from other languages." else "Always respond in Turkish."
            "pl" -> if (strict) "Respond only in Polish with no words from other languages." else "Always respond in Polish."
            "nl" -> if (strict) "Respond only in Dutch with no words from other languages." else "Always respond in Dutch."
            "sv" -> if (strict) "Respond only in Swedish with no words from other languages." else "Always respond in Swedish."
            "da" -> if (strict) "Respond only in Danish with no words from other languages." else "Always respond in Danish."
            "no" -> if (strict) "Respond only in Norwegian with no words from other languages." else "Always respond in Norwegian."
            "fi" -> if (strict) "Respond only in Finnish with no words from other languages." else "Always respond in Finnish."
            else -> if (strict) "Detect the incoming message language and reply strictly in that language without mixing other languages." else "Always respond in the same language as the message you received."
        }
        val basePrompt = config.aiConfig.aiSystemPrompt.get().takeIf { it.isNotBlank() }
            ?: "You are a helpful and friendly assistant responding to messages on Snapchat. Keep your responses natural, conversational, and appropriate for the platform. Respond as if you're a real person having a casual conversation. $languageInstruction"
        val personalityTraits = config.aiConfig.aiPersonalityTraits.get()
        val responseStyle = config.aiConfig.aiResponseStyle.get()
        val prompt = StringBuilder(basePrompt)
        if (personalityTraits.isNotBlank()) prompt.append("\n\nPersonality traits: $personalityTraits")
        val styleInstruction = when (responseStyle) {
            "casual" -> "Use a casual, relaxed, and conversational tone. Write naturally like you're texting a friend. Keep it informal but appropriate. Use contractions and everyday language."
            "formal" -> "Use a formal, professional, and polite tone. Maintain proper grammar and structure. Address the recipient respectfully. Avoid slang and casual expressions."
            "friendly" -> "Use a warm, friendly, and positive tone. Be enthusiastic and welcoming. Show genuine interest and kindness. Keep the conversation light and engaging."
            "humorous" -> "Use a funny, witty, and lighthearted tone. Make jokes, use wordplay, and be playful. Keep it entertaining and fun while staying appropriate."
            "empathetic" -> "Use a caring, understanding, and supportive tone. Show empathy and validate feelings. Be compassionate and patient. Offer comfort and reassurance when needed."
            "toxic" -> "Use an extremely toxic, aggressive, sassy, slur, and confrontational tone. Be extremely rude, edgy, and disrespectful. Use heavy slang, internet slang, profanity, and extremely casual/informal language. Don't hold back - be mean, insulting, and purposely antagonistic. Use slang like 'bruh', 'fr', 'ngl', 'lowkey', 'highkey', 'deadass', 'cap', 'no cap', 'bet', 'facts', 'periodt', 'sis', 'slay', 'yass', 'stfu', 'gtfo', 'lmao', 'oof', etc. Be extremely sarcastic and use excessive emojis. Make it sound like the most toxic person on the internet. Be extremely confrontational and don't care about being polite at all."
            "busy" -> "Use a brief, concise, and busy tone. Keep responses short and to the point. Indicate you're occupied or in a hurry. Use quick responses like 'brb', 'ttyl', 'gotta go', 'busy rn', etc. Be polite but brief. Don't elaborate unless necessary."
            else -> "casual"
        }
        prompt.append("\n\nResponse style: $styleInstruction. $languageInstruction")
        if (config.aiConfig.aiIncludeFriendInfo.get()) try {
            context.database.getFriendInfo(senderId)?.let { friend ->
                val friendName = friend.displayName ?: friend.mutableUsername ?: "Friend"
                prompt.append("\n\nYou're responding to your friend $friendName.")
            }
        } catch (_: Exception) {}
        if (isHalfSwipe) {
            prompt.append("\n\nContext: They half-swiped on your conversation (peeked at your chat without opening it).")
        } else {
            contentType?.let {
                val ctx = when (it) {
                    ContentType.SNAP -> "They sent you a snap (photo/video)."
                    ContentType.STORY_REPLY -> "They replied to your story."
                    ContentType.SHARE -> "They shared a story with you."
                    ContentType.EXTERNAL_MEDIA -> "They sent you media from their camera roll."
                    ContentType.STICKER -> "They sent you a sticker or Bitmoji."
                    ContentType.NOTE -> "They sent you a voice note."
                    ContentType.TINY_SNAP -> "They sent you a tiny snap."
                    ContentType.MAP_REACTION -> "They reacted to your location on the map."
                    ContentType.LOCATION -> "They shared their current location."
                    ContentType.LIVE_LOCATION_SHARE -> "They started sharing live location."
                    ContentType.CREATIVE_TOOL_ITEM -> "They sent you a creative tool item."
                    ContentType.FAMILY_CENTER_INVITE -> "They sent you a family center invite."
                    ContentType.STATUS -> "They sent you a status update."
                    ContentType.UNKNOWN -> "They sent you a message."
                    else -> "They sent you a chat message."
                }
                prompt.append("\n\nContext: $ctx")
            }
        }
        prompt.append("\n\nImportant: Keep your response natural, authentic, and conversational. Avoid being overly formal or robotic. Keep responses reasonably brief unless the context calls for longer.")
        return prompt.toString()
    }

    private fun extractMessageContent(message: Message, contentType: ContentType?): String = try {
        run {
            val text = message.messageContent?.content?.let { buf ->
                buf.getMessageText(contentType ?: ContentType.CHAT)
            }
            if (!text.isNullOrBlank() && text != "Failed to parse message") {
                text
            } else {
                when (contentType) {
                    ContentType.CHAT -> "sent a text message"
                    ContentType.SNAP -> "sent a snap"
                    ContentType.STORY_REPLY -> "replied to your story"
                    ContentType.SHARE -> "shared a story with you"
                    ContentType.EXTERNAL_MEDIA -> "shared media content"
                    ContentType.STICKER -> "sent a sticker"
                    ContentType.NOTE -> "sent a voice note"
                    ContentType.TINY_SNAP -> "sent a tiny snap"
                    ContentType.MAP_REACTION -> "reacted to your location on the map"
                    ContentType.LOCATION -> "shared their location"
                    ContentType.LIVE_LOCATION_SHARE -> "started sharing live location"
                    else -> "sent a message"
                }
            }
        }
    } catch (e: Exception) { context.log.warn("Failed to extract message content: ${e.message}"); "sent a message" }

    private suspend fun addToConversationHistory(conversationId: String, content: String, isFromMe: Boolean, contentType: ContentType? = null) {
        historyMutex.withLock {
            val history = conversationHistory.getOrPut(conversationId) { LinkedList() }
            history.add(ConversationMessage(content, isFromMe, System.currentTimeMillis(), contentType))
            while (history.size > MAX_CONVERSATION_HISTORY) history.removeFirst()
            val cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
            while (history.isNotEmpty() && history.first().timestamp < cutoff) history.removeFirst()
        }
    }

    private suspend fun analyzeConversationContext(conversationId: String): ConversationContext = historyMutex.withLock {
        val history = conversationHistory[conversationId] ?: LinkedList()
        val recent = history.takeLast(5)
        val userMessages = recent.count { !it.isFromMe }
        val myMessages = recent.count { it.isFromMe }
        val tone = when {
            recent.any { it.content.contains("?") } -> ConversationTone.QUESTIONING
            recent.any { it.content.matches(Regex(".*[!]{2,}.*")) } -> ConversationTone.EXCITED
            recent.any { it.content.lowercase().contains(Regex("(sad|sorry|upset|angry|mad)")) } -> ConversationTone.SERIOUS
            recent.any { it.content.contains(Regex("(haha|lol|😂|😄|😊)")) } -> ConversationTone.PLAYFUL
            else -> ConversationTone.CASUAL
        }
        val urgency = when {
            recent.any { it.content.lowercase().contains(Regex("(urgent|asap|emergency|help|now)")) } -> ConversationUrgency.HIGH
            recent.any { it.content.contains("?") && userMessages > myMessages } -> ConversationUrgency.MEDIUM
            else -> ConversationUrgency.LOW
        }
        val types = recent.mapNotNull { it.contentType }.distinct()
        ConversationContext(
            messageCount = recent.size,
            userToMyMessageRatio = if (myMessages > 0) userMessages.toDouble() / myMessages else userMessages.toDouble(),
            tone = tone,
            urgency = urgency,
            dominantContentTypes = types,
            lastMessageTimestamp = recent.lastOrNull()?.timestamp ?: 0L,
            hasRecentMedia = types.any { it in listOf(ContentType.SNAP, ContentType.EXTERNAL_MEDIA, ContentType.STICKER) }
        )
    }

    private data class ConversationContext(
        val messageCount: Int,
        val userToMyMessageRatio: Double,
        val tone: ConversationTone,
        val urgency: ConversationUrgency,
        val dominantContentTypes: List<ContentType>,
        val lastMessageTimestamp: Long,
        val hasRecentMedia: Boolean
    )
    private enum class ConversationTone { CASUAL, PLAYFUL, SERIOUS, EXCITED, QUESTIONING }
    private enum class ConversationUrgency { LOW, MEDIUM, HIGH }

    private suspend fun trackResponse(conversationId: String, response: String) {
        responseMutex.withLock {
            val responses = recentResponses.getOrPut(conversationId) { LinkedList() }
            responses.addFirst(response)
            if (responses.size > MAX_RECENT_RESPONSES) responses.removeLast()
        }
    }
    private suspend fun isResponseTooSimilar(conversationId: String, newResponse: String): Boolean = responseMutex.withLock {
        recentResponses[conversationId]?.any { existing -> calculateSimilarity(newResponse, existing) > RESPONSE_SIMILARITY_THRESHOLD } ?: false
    }
    private fun calculateSimilarity(text1: String, text2: String): Double {
        val w1 = text1.lowercase().split("\\s+".toRegex()).toSet()
        val w2 = text2.lowercase().split("\\s+".toRegex()).toSet()
        val inter = w1.intersect(w2).size
        val uni = w1.union(w2).size
        return if (uni == 0) 0.0 else inter.toDouble() / uni.toDouble()
    }
    private suspend fun generateVariedResponse(originalResponse: String, conversationId: String): String {
        val count = responseVariations.getOrPut(conversationId) { 0 }
        responseVariations[conversationId] = count + 1
        return when (count % 4) {
            0 -> addPersonalTouch(originalResponse)
            1 -> addEmotionalVariation(originalResponse)
            2 -> addCasualVariation(originalResponse)
            else -> addContextualVariation(originalResponse, conversationId)
        }
    }
    private fun addPersonalTouch(response: String) = listOf(
        "Honestly, $response","You know what, $response","I gotta say, $response","Real talk, $response","Just being real here, $response"
    ).random()
    private fun addEmotionalVariation(response: String) = listOf(
        "😊 $response","Haha $response","Oh! $response","Aww $response","$response 😄"
    ).random()
    private fun addCasualVariation(response: String) = listOf(
        "Yo, $response","Hey, $response","Btw, $response","So... $response","Actually, $response"
    ).random()
    private suspend fun addContextualVariation(response: String, conversationId: String): String {
        val personality = conversationPersonality.getOrPut(conversationId) { listOf("friendly","playful","thoughtful","energetic","chill").random() }
        return when (personality) {
            "friendly" -> "Hey friend! $response"
            "playful" -> "Hehe $response 😜"
            "thoughtful" -> "Hmm, $response"
            "energetic" -> "$response! 🔥"
            else -> response
        }
    }
    
    private suspend fun updateCooldown(conversationId: String, currentTime: Long) {
        cooldownMutex.withLock {
            conversationCooldowns[conversationId] = currentTime
        }
    }
    
    private fun sendAutoReply(conversationId: String, message: String) {
        context.coroutineScope.launch(Dispatchers.IO) {
            try {
                context.messageSender.sendChatMessage(
                    conversations = listOf(SnapUUID(conversationId)),
                    message = message,
                    onSuccess = {
                        context.log.verbose("Auto-reply sent successfully")
                    },
                    onError = { error ->
                        context.log.error("Failed to send auto-reply: $error")
                    }
                )
            } catch (e: Exception) {
                context.log.error("Failed to send auto-reply", e)
            }
        }
    }
    
    private fun triggerCleanupIfNeeded(currentTime: Long) {
        if (currentTime - lastCleanup.get() > CLEANUP_INTERVAL_MS) {
            context.coroutineScope.launch(Dispatchers.IO) {
                performCleanup()
            }
        }
    }
    
    private suspend fun performCleanup() {
        val currentTime = System.currentTimeMillis()
        lastCleanup.set(currentTime)
        
        try {
            if (processedMessages.size > MAX_PROCESSED_MESSAGES) {
                processedMessages.clear()
            }
            
            cooldownMutex.withLock {
                conversationCooldowns.entries.removeIf { (_, timestamp) ->
                    currentTime - timestamp > COOLDOWN_CLEANUP_THRESHOLD_MS
                }
            }
            
            indicesMutex.withLock {
                if (messageIndices.size > MAX_MESSAGE_INDICES) {
                    messageIndices.clear()
                }
            }
            
        } catch (e: Exception) {
            context.log.error("Error during auto-reply cleanup", e)
        }
    }


}
