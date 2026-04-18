package cock.crest.purrfectsnap.lite.core.features.impl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.gson.JsonObject
import cock.crest.purrfectsnap.lite.common.data.FriendLinkType
import cock.crest.purrfectsnap.lite.common.database.impl.FriendInfo
import cock.crest.purrfectsnap.lite.core.event.events.impl.NetworkApiRequestEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.EvictingMap
import java.io.InputStreamReader
import java.util.Calendar

class FriendMutationObserver: Feature("FriendMutationObserver") {
    private val translation by lazy { context.translation.getCategory("friend_mutation_observer") }
    private val addSourceCache = EvictingMap<String, String>(500)

    private val notificationManager by lazy { context.androidContext.getSystemService(NotificationManager::class.java) }
    private val channelId by lazy {
        "friend_mutation_observer".also {
            notificationManager.createNotificationChannel(
                NotificationChannel(it, translation["notification_channel_name"], NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    // Injected from app layer — keeps core free of ui.* imports
    var aphelionToastProvider: ((
        icon: ImageVector,
        text: String,
        bitmojiUrl: String?,
        onDismiss: () -> Unit
    ) -> Unit)? = null

    fun getFriendAddSource(userId: String): String? = addSourceCache[userId]

    private fun sendMutationNotification(icon: ImageVector, contentText: String, friendInfo: FriendInfo? = null) {
        val currentTheme = context.config.global.uiSettings.managerTheme.get()
        val isAphelion = currentTheme == "APHELION"

        notificationManager.notify(System.nanoTime().toInt(),
            Notification.Builder(context.androidContext, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(translation["notification_channel_name"])
                .setContentText(contentText)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .build()
        )

        val provider = aphelionToastProvider
        if (isAphelion && provider != null) {
            val bitmojiUrl = friendInfo?.let {
                cock.crest.purrfectsnap.lite.common.util.snap.BitmojiSelfie.getBitmojiSelfie(
                    it.bitmojiSelfieId,
                    it.bitmojiAvatarId,
                    cock.crest.purrfectsnap.lite.common.util.snap.BitmojiSelfie.BitmojiSelfieType.NEW_THREE_D
                )
            }
            provider(icon, contentText, bitmojiUrl) {
                // onDismiss handled inside the provider lambda in app layer
            }
        } else {
            context.inAppOverlay.showStatusToast(icon, contentText, durationMs = 7000)
        }
    }

    private fun formatUsername(friendInfo: FriendInfo): String {
        return friendInfo.displayName?.takeIf { it.isNotBlank() }?.let {
            "$it (${friendInfo.mutableUsername})"
        } ?: friendInfo.mutableUsername ?: ""
    }

    private fun prettyPrintBirthday(month: Int, day: Int): String {
        val calendar = Calendar.getInstance()
        calendar[Calendar.MONTH] = month
        return calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, context.translation.loadedLocale)?.toString() + " " + day
    }

    override fun init() {
        val config by context.config.messaging.friendMutationNotifier
        context.event.subscribe(NetworkApiRequestEvent::class) { event ->
            if (!event.url.contains("ami/friends")) return@subscribe
            event.onSuccess { buffer ->
                runCatching {
                    val jsonObject = context.gson.fromJson(InputStreamReader(buffer?.inputStream() ?: return@onSuccess, Charsets.UTF_8), JsonObject::class.java)
                    jsonObject.getAsJsonArray("added_friends")?.map { it.asJsonObject }?.forEach { friend ->
                        val userId = friend.get("user_id").asString
                        (friend.get("add_source")?.asString?.takeIf { it.isNotBlank() }
                            ?: friend.get("add_source_type")?.asString?.takeIf { it.isNotBlank() })?.let {
                            addSourceCache[userId] = it
                        }
                    }

                    if (config.isEmpty()) return@runCatching
                    jsonObject.getAsJsonArray("friends")?.map { it.asJsonObject }?.forEach { friend ->
                        runCatching {
                            val userId = friend.get("user_id")?.asString ?: return@forEach
                            if (userId == context.database.myUserId) return@forEach
                            val databaseFriend = context.database.getFriendInfo(userId) ?: return@forEach
                            if (FriendLinkType.fromValue(databaseFriend.friendLinkType) != FriendLinkType.MUTUAL) return@forEach

                            if (config.contains("remove_friend") && friend.get("direction")?.asString == "OUTGOING" && !friend.has("fidelius_info")) {
                                sendMutationNotification(Icons.Default.PersonRemove, translation.format("friend_removed", "username" to formatUsername(databaseFriend)), databaseFriend)
                                return@forEach
                            }

                            if (config.contains("birthday_changes") &&
                                databaseFriend.birthday.takeIf { it != 0L }?.let {
                                    ((it shr 32).toInt()).toString().padStart(2, '0') + "-" + (it.toInt()).toString().padStart(2, '0')
                                } != friend.get("birthday")?.asString
                            ) {
                                val oldBirthday = databaseFriend.birthday.takeIf { it != 0L }?.let { prettyPrintBirthday((it shr 32).toInt() - 1, it.toInt()) }
                                if (!friend.has("birthday")) {
                                    sendMutationNotification(Icons.Default.Cake, translation.format("birthday_removed", "username" to formatUsername(databaseFriend), "birthday" to oldBirthday.orEmpty()), databaseFriend)
                                } else {
                                    val newBirthday = friend.get("birthday")?.asString?.split("-")?.let { prettyPrintBirthday(it[0].toInt() - 1, it[1].toInt()) }
                                    if (oldBirthday == null) {
                                        sendMutationNotification(Icons.Default.Cake, translation.format("birthday_added", "username" to formatUsername(databaseFriend), "birthday" to newBirthday.orEmpty()), databaseFriend)
                                    } else {
                                        sendMutationNotification(Icons.Default.Cake, translation.format("birthday_changed", "username" to formatUsername(databaseFriend), "oldBirthday" to oldBirthday, "newBirthday" to newBirthday.orEmpty()), databaseFriend)
                                    }
                                }
                            }

                            if (config.contains("bitmoji_avatar_changes") && databaseFriend.bitmojiAvatarId != friend.get("bitmoji_avatar_id")?.asString) {
                                sendMutationNotification(Icons.Default.Face, translation.format("bitmoji_avatar_changed", "username" to formatUsername(databaseFriend)), databaseFriend)
                            }

                            if (config.contains("bitmoji_selfie_changes") && databaseFriend.bitmojiSelfieId != friend.get("bitmoji_selfie_id")?.asString) {
                                sendMutationNotification(Icons.Default.Face, translation.format("bitmoji_selfie_changed", "username" to formatUsername(databaseFriend)), databaseFriend)
                            }

                            if (config.contains("bitmoji_background_changes") && databaseFriend.bitmojiBackgroundId != friend.get("bitmoji_background_id")?.asString) {
                                sendMutationNotification(Icons.Default.Image, translation.format("bitmoji_background_changed", "username" to formatUsername(databaseFriend)), databaseFriend)
                            }

                            if (config.contains("bitmoji_scene_changes") && databaseFriend.bitmojiSceneId != friend.get("bitmoji_scene_id")?.asString) {
                                sendMutationNotification(Icons.Default.Landscape, translation.format("bitmoji_scene_changed", "username" to formatUsername(databaseFriend)), databaseFriend)
                            }
                        }.onFailure { context.log.error("Failed to process friend", it) }
                    }
                }.onFailure { context.log.error("Failed to process friends", it) }
            }
        }
    }
}
