package cock.crest.purrfectsnap.lite.core.event.events.impl

import android.view.View
import android.view.ViewGroup
import cock.crest.purrfectsnap.lite.core.event.events.AbstractHookEvent

class LayoutInflateEvent(
    val layoutId: Int,
    val parent: ViewGroup?,
    val view: View?
) : AbstractHookEvent()