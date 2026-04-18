package cock.crest.purrfectsnap.lite.core.features.impl.spying

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import cock.crest.purrfectsnap.lite.common.Constants
import cock.crest.purrfectsnap.lite.core.features.Feature
import kotlin.time.Duration.Companion.milliseconds

class HalfSwipeNotifier : Feature("Half Swipe Notifier") {
    private val startPeekingTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val halfSwipeListeners = mutableListOf<(String, String, Long) -> Unit>()

    private val notificationManager get() = context.androidContext.getSystemService(NotificationManager::class.java)
    private val translation by lazy { context.translation.getCategory("half_swipe_notifier")}
    private val channelId by lazy {
        "peeking".also {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    it,
                    translation["notification_channel_name"],
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    fun addOnHalfSwipeListener(listener: (conversationId: String, userId: String, duration: Long) -> Unit) {
        halfSwipeListeners.add(listener)
    }

    override fun init() {
        if (context.config.messaging.halfSwipeNotifier.globalState != true) return
        context.feature(FriendTracker::class).addOnPeekingStateChangedListener { conversationId, userId, peeking ->
            if (peeking) {
                startPeeking(conversationId, userId)
            } else {
                endPeeking(conversationId, userId)
            }
        }
    }

    private fun startPeeking(conversationId: String, userId: String) {
        startPeekingTimestamps[conversationId + userId] = System.currentTimeMillis()
    }

    private fun endPeeking(conversationId: String, userId: String) {
        startPeekingTimestamps[conversationId + userId]?.let { startPeekingTimestamp ->
            val peekingDuration = (System.currentTimeMillis() - startPeekingTimestamp).milliseconds.inWholeSeconds
            val minDuration = context.config.messaging.halfSwipeNotifier.minDuration.get().toLong()
            val maxDuration = context.config.messaging.halfSwipeNotifier.maxDuration.get().toLong()

            if (minDuration > peekingDuration || maxDuration < peekingDuration) return

            // Notify listeners about the half-swipe
            halfSwipeListeners.forEach { listener ->
                runCatching {
                    listener(conversationId, userId, peekingDuration)
                }.onFailure {
                    context.log.error("Error in half-swipe listener", it)
                }
            }

            val feedEntry = context.database.getFeedEntryByConversationId(conversationId)
            val friendInfo = context.database.getFriendInfo(userId) ?: return

            Notification.Builder(context.androidContext, channelId)
                .setContentTitle(feedEntry?.feedDisplayName ?: friendInfo.displayName ?: friendInfo.mutableUsername)
                .setContentText(if (feedEntry?.conversationType == 1) {
                    translation.format("notification_content_group",
                        "friend" to (friendInfo.displayName ?: friendInfo.mutableUsername).toString(),
                        "group" to (feedEntry.feedDisplayName ?: "Group"),
                        "duration" to peekingDuration.toString()
                    )
                } else {
                    translation.format("notification_content_dm",
                        "friend" to (friendInfo.displayName ?: friendInfo.mutableUsername).toString(),
                        "duration" to peekingDuration.toString()
                    )
                })
                .setContentIntent(
                    context.androidContext.packageManager.getLaunchIntentForPackage(
                        Constants.SNAPCHAT_PACKAGE_NAME
                    )?.let {
                        PendingIntent.getActivity(
                            context.androidContext,
                            0, it, PendingIntent.FLAG_IMMUTABLE
                        )
                    }
                )
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setAutoCancel(true)
                .setSmallIcon(android.R.drawable.presence_invisible)
                .build()
                .let { notification ->
                    notificationManager.notify(System.nanoTime().toInt(), notification)
                }
        }
    }
}
