package cock.crest.purrfectsnap.lite.core.event.events.impl

import android.content.Context
import android.content.Intent
import cock.crest.purrfectsnap.lite.core.event.events.AbstractHookEvent

class SnapWidgetBroadcastReceiveEvent(
    val androidContext: Context,
    val intent: Intent?,
    val action: String
) : AbstractHookEvent()