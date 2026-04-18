package cock.crest.purrfectsnap.lite.bridge

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import cock.crest.purrfectsnap.lite.SharedContextHolder
import cock.crest.purrfectsnap.lite.common.Constants

class ForceStartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("streaks_notification_action", false)) {
            packageManager.getLaunchIntentForPackage(Constants.SNAPCHAT_PACKAGE_NAME)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(this)
            }
            SharedContextHolder.remote(this).streaksReminder.dismissAllNotifications()
        }
        finish()
    }
}