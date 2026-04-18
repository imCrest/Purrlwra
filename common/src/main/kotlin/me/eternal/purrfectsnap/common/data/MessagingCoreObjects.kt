package cock.crest.purrfectsnap.lite.common.data

import android.database.Cursor
import android.os.Parcelable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.parcelize.Parcelize
import cock.crest.purrfectsnap.lite.common.config.FeatureNotice
import cock.crest.purrfectsnap.lite.common.data.download.toKeyPair
import cock.crest.purrfectsnap.lite.common.util.ktx.getIntOrNull
import cock.crest.purrfectsnap.lite.common.util.ktx.getInteger
import cock.crest.purrfectsnap.lite.common.util.ktx.getLongOrNull
import cock.crest.purrfectsnap.lite.common.util.ktx.getStringOrNull
import kotlin.time.Duration.Companion.hours


enum class RuleState(
    val key: String
) {
    BLACKLIST("blacklist"),
    WHITELIST("whitelist");

    companion object {
        fun getByName(name: String) = entries.first { it.key == name }
    }
}

enum class SocialScope(
    val key: String,
    val tabRoute: String,
) {
    FRIEND("friend", "friend_info/{id}"),
    GROUP("group", "group_info/{id}");

    companion object {
        fun getByName(name: String) = entries.first { it.key == name }
    }
}

enum class MessagingRuleType(
    val key: String,
    val listMode: Boolean,
    val icon: ImageVector,
    val showInFriendMenu: Boolean = true,
    val defaultValue: String? = "whitelist",
    val configNotices: Array<FeatureNotice> = emptyArray()
) {
    STEALTH("stealth", true, Icons.Outlined.TrackChanges),
    SNAP_STEALTH("snap_stealth", true, Icons.Outlined.PhotoCamera, showInFriendMenu = false),
    CHAT_STEALTH("chat_stealth", true, Icons.Outlined.ChatBubbleOutline, showInFriendMenu = false),
    HIDE_TYPING_INDICATOR("hide_typing_indicator", true, Icons.Outlined.KeyboardHide, defaultValue = "whitelist"),
    AUTO_DOWNLOAD("auto_download", true, Icons.Outlined.DownloadForOffline),
    AUTO_SAVE("auto_save", true, Icons.Outlined.Save, defaultValue = "blacklist"),
    AUTO_OPEN_SNAPS("auto_open_snaps", true, Icons.Outlined.OpenInFull, configNotices = arrayOf(FeatureNotice.BAN_RISK, FeatureNotice.UNSTABLE), defaultValue = null),
    UNSAVEABLE_MESSAGES("unsaveable_messages", true, Icons.Outlined.FolderOff, defaultValue = null),
    HIDE_FRIEND_FEED("hide_friend_feed", false, Icons.Outlined.VisibilityOff, showInFriendMenu = false),
    E2E_ENCRYPTION("e2e_encryption", false, Icons.Outlined.Lock),
    PIN_CONVERSATION("pin_conversation", false, Icons.Outlined.PushPin, showInFriendMenu = false),
    MESSAGE_LOGGER("message_logger", true, Icons.AutoMirrored.Filled.Message, showInFriendMenu = true, defaultValue = "blacklist"),
    AUTO_READ("auto_read", true, Icons.Outlined.DoneAll, defaultValue = "whitelist"),
    AUTO_REPLY("auto_reply", true, Icons.AutoMirrored.Outlined.Reply, defaultValue = "blacklist"),
    AUTO_DELETE_SENT_MESSAGES("auto_delete_sent_messages", true, Icons.Outlined.DeleteSweep, defaultValue = "blacklist");

    fun translateOptionKey(optionKey: String): String {
        return if (listMode) "rules.properties.$key.options.$optionKey" else "rules.properties.$key.name"
    }

    companion object {
        fun getByName(name: String) = entries.firstOrNull { it.key == name }
    }
}

private val partialStealthRuleTypes = setOf(
    MessagingRuleType.SNAP_STEALTH,
    MessagingRuleType.CHAT_STEALTH
)

private val allStealthRuleTypes = partialStealthRuleTypes + MessagingRuleType.STEALTH

fun MessagingRuleType.isStealthRule(): Boolean = this in allStealthRuleTypes

fun Collection<MessagingRuleType>.normalizeStealthRules(): Set<MessagingRuleType> {
    val normalizedRules = toMutableSet()
    if (MessagingRuleType.STEALTH in normalizedRules) {
        normalizedRules.removeAll(partialStealthRuleTypes)
        return normalizedRules
    }

    if (partialStealthRuleTypes.all { it in normalizedRules }) {
        normalizedRules.removeAll(partialStealthRuleTypes)
        normalizedRules.add(MessagingRuleType.STEALTH)
    }

    return normalizedRules
}

fun Collection<MessagingRuleType>.withNormalizedRuleToggle(
    ruleType: MessagingRuleType,
    enabled: Boolean
): Set<MessagingRuleType> {
    val updatedRules = toMutableSet().apply {
        if (enabled) {
            add(ruleType)
        } else {
            remove(ruleType)
        }
    }

    if (!ruleType.isStealthRule()) {
        return updatedRules
    }

    if (enabled) {
        when (ruleType) {
            MessagingRuleType.STEALTH -> updatedRules.removeAll(partialStealthRuleTypes)
            MessagingRuleType.SNAP_STEALTH,
            MessagingRuleType.CHAT_STEALTH -> updatedRules.remove(MessagingRuleType.STEALTH)
            else -> Unit
        }
    }

    return updatedRules.normalizeStealthRules()
}

@Parcelize
data class FriendStreaks(
    val notify: Boolean = true,
    val expirationTimestamp: Long,
    val length: Int
): Parcelable {
    fun hoursLeft() = (expirationTimestamp - System.currentTimeMillis()) / 1000 / 60 / 60

    fun isAboutToExpire(expireHours: Int) = (expirationTimestamp - System.currentTimeMillis()).let {
        it > 0 && it < expireHours.hours.inWholeMilliseconds
    }
}

@Parcelize
data class MessagingGroupInfo(
    val conversationId: String,
    val name: String,
    val participantsCount: Int
): Parcelable {
    companion object {
        fun fromCursor(cursor: Cursor): MessagingGroupInfo {
            return MessagingGroupInfo(
                conversationId = cursor.getStringOrNull("conversationId")!!,
                name = cursor.getStringOrNull("name")!!,
                participantsCount = cursor.getInteger("participantsCount")
            )
        }
    }
}

@Parcelize
data class MessagingFriendInfo(
    val userId: String,
    val dmConversationId: String?,
    val displayName: String?,
    val mutableUsername: String,
    val bitmojiId: String?,
    val selfieId: String?,
    var streaks: FriendStreaks?,
): Parcelable {
    companion object {
        fun fromCursor(cursor: Cursor): MessagingFriendInfo {
            return MessagingFriendInfo(
                userId = cursor.getStringOrNull("userId")!!,
                dmConversationId = cursor.getStringOrNull("dmConversationId"),
                displayName = cursor.getStringOrNull("displayName"),
                mutableUsername = cursor.getStringOrNull("mutableUsername")!!,
                bitmojiId = cursor.getStringOrNull("bitmojiId"),
                selfieId = cursor.getStringOrNull("selfieId"),
                streaks = cursor.getLongOrNull("expirationTimestamp")?.let {
                    FriendStreaks(
                        notify = cursor.getIntOrNull("notify") == 1,
                        expirationTimestamp = it,
                        length = cursor.getIntOrNull("length") ?: 0
                    )
                }
            )
        }
    }
}

class StoryData(
    val url: String,
    val postedAt: Long,
    val createdAt: Long,
    val key: ByteArray?,
    val iv: ByteArray?
) {
    fun getEncryptionKeyPair() = key?.let { (it to (iv ?: return@let null)) }?.toKeyPair()
}
