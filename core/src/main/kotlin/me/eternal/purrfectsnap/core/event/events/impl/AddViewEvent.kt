package cock.crest.purrfectsnap.lite.core.event.events.impl

import android.view.View
import android.view.ViewGroup
import cock.crest.purrfectsnap.lite.core.event.events.AbstractHookEvent

class AddViewEvent(
    val parent: ViewGroup,
    var view: View,
    var index: Int,
    var layoutParams: ViewGroup.LayoutParams
) : AbstractHookEvent() {
    val viewClassName by lazy { view.javaClass.name }
}