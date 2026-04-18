package cock.crest.purrfectsnap.lite.core.wrapper.impl

import cock.crest.purrfectsnap.lite.core.wrapper.AbstractWrapper

class MessageDestinations(obj: Any) : AbstractWrapper(obj){
    var conversations by field("mConversations", uuidArrayListMapper)
    var stories by field<ArrayList<*>>("mStories")
    var mPhoneNumbers by field<ArrayList<*>>("mPhoneNumbers")
    var massSnaps by field<ArrayList<*>>("mMassSnaps")
}
