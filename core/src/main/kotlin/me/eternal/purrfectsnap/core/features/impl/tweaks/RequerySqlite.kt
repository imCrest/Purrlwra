package cock.crest.purrfectsnap.lite.core.features.impl.tweaks

import cock.crest.purrfectsnap.lite.common.data.MessagingRuleType
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook

class RequerySqlite : Feature("Requery Sqlite") {
    override fun init() {
        val hideQuickAddSuggestions = context.config.userInterface.hideQuickAddSuggestions.get()
        val hideFriendFeedEntry = context.config.userInterface.hideFriendFeedEntry.get()
        val hideSuggestedStories = context.config.userInterface.hideStorySuggestions.get().contains("hide_suggested_friend_stories")

        if (!hideQuickAddSuggestions && !hideFriendFeedEntry && !hideSuggestedStories) return

        findClass("io.requery.android.database.sqlite.SQLiteDatabase").hook("rawQueryWithFactory", HookStage.BEFORE) { param ->
            var sqlRequest = param.argNullable<String>(1) ?: return@hook
            val sqlUpper = sqlRequest.uppercase().trim()

            fun patchRequest(condition: String) {
                sqlRequest.lastIndexOf("WHERE").takeIf { it != -1 }?.let {
                    sqlRequest = sqlRequest.substring(0, it + 5) + " $condition AND " + sqlRequest.substring(it + 5)
                    param.setArg(1, sqlRequest)
                }
            }

            fun isSuggestionQuery() = sqlRequest.contains("SuggestedFriendPlacement") ||
                                     sqlRequest.contains("TopSuggestedFriend") ||
                                     sqlRequest.contains("TopSuggestedFriendV2") ||
                                     sqlRequest.contains("SuggestedFriend")

            if (hideQuickAddSuggestions && sqlUpper.startsWith("SELECT") && isSuggestionQuery()) {
                val isDisplayQuery = sqlRequest.contains("FriendWithUsername") ||
                                    sqlRequest.contains("FROM TopSuggestedFriend") ||
                                    (sqlRequest.contains("UNION") && sqlRequest.contains("FROM SuggestedFriend"))
                val isCountQuery = sqlUpper.contains("SELECT 0") || sqlUpper.contains("SELECT COUNT")
                
                if (isDisplayQuery || isCountQuery) {
                    patchRequest("0 = 1")
                }
            }

            if (hideSuggestedStories && sqlRequest.contains("DiscoverFeedFriendStoriesViewV2 AS DFStories")) {
                patchRequest("DFStories.isFriendOfFriend = 0")
            }

            if (hideFriendFeedEntry && sqlUpper.startsWith("SELECT") && sqlRequest.contains("FriendWithUsername") && sqlRequest.contains("userId")) {
                if (isSuggestionQuery()) return@hook
                
                val ids = context.bridgeClient.getRuleIds(MessagingRuleType.HIDE_FRIEND_FEED).takeIf { it.isNotEmpty() } ?: return@hook
                val userIdField = if (sqlRequest.contains("Friend.userId")) "Friend.userId" else "userId"
                patchRequest(ids.joinToString(" AND ") { "$userIdField != '$it'" })
            }
        }
    }
}
