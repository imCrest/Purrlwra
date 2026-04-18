package cock.crest.purrfectsnap.lite.core.features.impl.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.common.data.MessageUpdate
import cock.crest.purrfectsnap.lite.common.data.MessagingRuleType
import cock.crest.purrfectsnap.lite.common.ui.createComposeView
import cock.crest.purrfectsnap.lite.core.event.events.impl.BindViewEvent
import cock.crest.purrfectsnap.lite.core.event.events.impl.SendMessageWithContentEvent
import cock.crest.purrfectsnap.lite.core.features.MessagingRuleFeature
import cock.crest.purrfectsnap.lite.core.features.impl.messaging.Messaging
import cock.crest.purrfectsnap.lite.core.util.ktx.getObjectField
import kotlin.random.Random

class AutoDeleteSentMessages : MessagingRuleFeature("Auto Delete Sent Messages", MessagingRuleType.AUTO_DELETE_SENT_MESSAGES) {
    companion object {
    }
    
    private val pendingDeletions = mutableMapOf<String, Long>()
    private val countdownStates = mutableMapOf<String, MutableState<Int>>()
    private val activeViews = mutableMapOf<String, ViewGroup>()
    private val translation by lazy { context.translation.getCategory("auto_delete_sent_messages") }
    private val countdownTag = Random.nextLong().toString()
    private val notificationChannelId = "auto_delete_messages"
    private var notificationId = 1000

    

    
    override fun init() {
        val config = context.config.messaging.autoDeleteSentMessages
        
        if (config.globalState != true) {
            return
        }
        
        if (getRuleState() == null) return
        
        val deleteAfterValue = config.deleteAfterValue.get()
        val deleteAfterUnit = config.deleteAfterUnit.get()
        val showCountdown = config.showCountdown.get()
        val showNotification = config.showNotification.get()
        val messageTypes = config.messageTypes.get()
        val allowRunningInBackground = config.allowRunningInBackground.get()
        
        if (showNotification) {
            setupNotificationChannel()
        }
        


        

        if (allowRunningInBackground) {
            context.coroutineScope.launch {
                while (true) {
                    delay(5000)
                    if (context.isMainActivityPaused && pendingDeletions.isNotEmpty()) {
                        updatePersistentNotification()
                    }
                }
            }
        }
        
        onNextActivityCreate {
            context.event.subscribe(BindViewEvent::class) { event ->
                event.chatMessage { _, _ ->
                    val view = event.view as? ViewGroup ?: return@subscribe
                    view.findViewWithTag<View>(countdownTag)?.let { view.removeView(it) }
                    
                    val message = event.databaseMessage ?: return@chatMessage
                    val messageKey = "${message.clientConversationId}:${message.clientMessageId}"
                    
                    activeViews[messageKey] = view
                    
                    addCountdownToView(view, messageKey)
                }
            }
        }
        

        val deleteAfterSeconds = when (deleteAfterUnit) {
            "minutes" -> deleteAfterValue * 60
            "hours" -> deleteAfterValue * 3600
            else -> deleteAfterValue
        }
        
        context.event.subscribe(SendMessageWithContentEvent::class) { event ->
            val contentType = event.messageContent.contentType
            
            if (contentType == null || !messageTypes.contains(contentType.name)) {
                return@subscribe
            }
            
            val conversationId = event.destinations.conversations?.firstOrNull()?.toString()
            
            if (conversationId == null) {
                return@subscribe
            }
            

            if (!canUseRule(conversationId)) {
                return@subscribe
            }
            
            event.addCallbackResult("onSuccess") { args ->
                context.coroutineScope.launch {
                    delay(100)
                    
                    val messageId = try {
                        context.database.getMessagesFromConversationId(conversationId, 1)?.firstOrNull()?.clientMessageId?.toLong()
                    } catch (e: Exception) {
                        context.log.warn("Failed to query database for latest message: ${e.message}")
                        null
                    }
                    
                    if (messageId == null) {
                        return@launch
                    }
                    
                    val messageKey = "$conversationId:$messageId"
                    pendingDeletions[messageKey] = messageId
                    
                    if (showCountdown || showNotification) {
                        val countdownState = mutableStateOf(deleteAfterSeconds)
                        countdownStates[messageKey] = countdownState
                        
                        if (showNotification) {
                            showDeleteNotification(messageKey, deleteAfterSeconds)
                        }
                        
                        if (showCountdown) {
                            Handler(Looper.getMainLooper()).post {
                                activeViews[messageKey]?.let { view ->
                                    view.findViewWithTag<View>(countdownTag)?.let { view.removeView(it) }
                                    addCountdownToView(view, messageKey)
                                }
                            }
                        }
                        
                        for (i in deleteAfterSeconds downTo 1) {
                            if (!pendingDeletions.containsKey(messageKey)) {
                                break
                            }

                            
                            Handler(Looper.getMainLooper()).post {
                                countdownState.value = i
                            }
                            
                            if (showNotification && (i % 10 == 0 || i <= 10)) {
                                updateDeleteNotification(messageKey, i)
                            }
                            
                            delay(1000)
                        }
                        
                        Handler(Looper.getMainLooper()).post {
                            countdownStates.remove(messageKey)
                            activeViews.remove(messageKey)
                            if (showNotification) {
                                cancelNotification(messageKey)
                            }
                        }
                    } else {
                        delay(deleteAfterSeconds * 1000L)
                    }
                    
                    if (!pendingDeletions.containsKey(messageKey)) return@launch
                    
                    if (showNotification) {
                        cancelNotification(messageKey)
                    }
                    
                    deleteMessage(conversationId, messageId, messageKey)
                }
            }
        }
    }
    
    private fun addCountdownToView(view: ViewGroup, messageKey: String) {
        val countdownState = countdownStates[messageKey]
        if (countdownState != null && countdownState.value > 0) {
            createComposeView(view.context) {
                val remainingTime by countdownState
                
                if (remainingTime > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    Color.Red.copy(alpha = 0.8f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            val timeText = when {
                                remainingTime >= 3600 -> {
                                    val hours = remainingTime / 3600
                                    val minutes = (remainingTime % 3600) / 60
                                    val seconds = remainingTime % 60
                                    when {
                                        minutes == 0 && seconds == 0 -> "${hours}h"
                                        seconds == 0 -> "${hours}h ${minutes}m"
                                        else -> "${hours}h ${minutes}m ${seconds}s"
                                    }
                                }
                                remainingTime >= 60 -> {
                                    val minutes = remainingTime / 60
                                    val seconds = remainingTime % 60
                                    if (seconds == 0) "${minutes}m" else "${minutes}m ${seconds}s"
                                }
                                else -> "${remainingTime}s"
                            }
                            
                            Text(
                                text = "Deleting in $timeText",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }.apply {
                tag = countdownTag
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                view.addView(this)
            }
        }
    }
    
    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.androidContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelId,
                "Auto Delete Messages",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for auto-deleting messages"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun showDeleteNotification(messageKey: String, remainingSeconds: Int) {
        val notificationManager = context.androidContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val timeText = formatTime(remainingSeconds)
        
        val notification = NotificationCompat.Builder(context.androidContext, notificationChannelId)
            .setSmallIcon(android.R.drawable.ic_delete)
            .setContentTitle("Message Auto-Delete")
            .setContentText("Message will be deleted in $timeText")
            .setProgress(remainingSeconds, remainingSeconds, false)
            .setOngoing(true)
            .setSilent(true)
            .setAutoCancel(false)
            .setColor(0xFFE53E3E.toInt())
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Your message will be automatically deleted in $timeText. This notification will update as the countdown progresses."))
            .build()
            
        notificationManager.notify(getNotificationId(messageKey), notification)
    }
    
    private fun updateDeleteNotification(messageKey: String, remainingSeconds: Int) {
        val notificationManager = context.androidContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val timeText = formatTime(remainingSeconds)
        val initialTime = countdownStates[messageKey]?.value ?: remainingSeconds
        
        val notification = NotificationCompat.Builder(context.androidContext, notificationChannelId)
            .setSmallIcon(android.R.drawable.ic_delete)
            .setContentTitle("Message Auto-Delete")
            .setContentText("Message will be deleted in $timeText")
            .setProgress(initialTime, initialTime - remainingSeconds, false)
            .setOngoing(true)
            .setSilent(true)
            .setAutoCancel(false)
            .setColor(if (remainingSeconds <= 10) 0xFFFF0000.toInt() else 0xFFE53E3E.toInt())
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Your message will be automatically deleted in $timeText${if (remainingSeconds <= 10) "🕒" else ""}"))
            .build()
            
        notificationManager.notify(getNotificationId(messageKey), notification)
    }
    
    private fun cancelNotification(messageKey: String) {
        val notificationManager = context.androidContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(getNotificationId(messageKey))
    }
    
    private fun getNotificationId(messageKey: String): Int {
        return (notificationChannelId + messageKey).hashCode()
    }
    
    private fun formatTime(seconds: Int): String {
        return when {
            seconds >= 3600 -> {
                val hours = seconds / 3600
                val minutes = (seconds % 3600) / 60
                val secs = seconds % 60
                when {
                    minutes == 0 && secs == 0 -> "${hours}h"
                    secs == 0 -> "${hours}h ${minutes}m"
                    else -> "${hours}h ${minutes}m ${secs}s"
                }
            }
            seconds >= 60 -> {
                val minutes = seconds / 60
                val secs = seconds % 60
                if (secs == 0) "${minutes}m" else "${minutes}m ${secs}s"
            }
            else -> "${seconds}s"
        }
    }
    
    private fun deleteMessage(conversationId: String, messageId: Long, messageKey: String) {
        val conversationManager = context.feature(Messaging::class).conversationManager
        if (conversationManager == null) {
            pendingDeletions.remove(messageKey)
            return
        }
        
        conversationManager.updateMessage(
            conversationId,
            messageId,
            MessageUpdate.ERASE
        ) { error ->
            pendingDeletions.remove(messageKey)
            activeViews.remove(messageKey)
            
            Handler(Looper.getMainLooper()).post {
                if (error == null) {
                    context.shortToast(translation.get("delete_success_toast"))
                } else {
                    context.shortToast(translation.get("delete_failed_toast"))
                }
            }
        }
    }
    
    private fun updatePersistentNotification() {
        val notificationManager = context.androidContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val queueSize = pendingDeletions.size
        

        notificationManager.cancel(9999)
        return
    }
    
    private fun clearAllPendingDeletions() {
        pendingDeletions.clear()
        countdownStates.clear()
        activeViews.clear()
        
        val notificationManager = context.androidContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(9999)
        
        Handler(Looper.getMainLooper()).post {
            context.shortToast(translation["queue_cleared_toast"])
        }
    }
}
