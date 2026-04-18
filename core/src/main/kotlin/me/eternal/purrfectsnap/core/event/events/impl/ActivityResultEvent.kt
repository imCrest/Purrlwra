package cock.crest.purrfectsnap.lite.core.event.events.impl

import android.app.Activity
import android.content.Intent
import cock.crest.purrfectsnap.lite.core.event.events.AbstractHookEvent

class ActivityResultEvent(
    val activity: Activity,
    val requestCode: Int,
    val resultCode: Int,
    val intent: Intent
): AbstractHookEvent()