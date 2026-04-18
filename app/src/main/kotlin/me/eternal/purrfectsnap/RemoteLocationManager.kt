package cock.crest.purrfectsnap.lite

import cock.crest.purrfectsnap.lite.bridge.location.FriendLocation
import cock.crest.purrfectsnap.lite.bridge.location.LocationManager

class RemoteLocationManager(
    private val remoteSideContext: RemoteSideContext
): LocationManager.Stub() {
    var friendsLocation = listOf<FriendLocation>()
        private set

    override fun provideFriendsLocation(friendsLocation: List<FriendLocation>) {
        this.friendsLocation = friendsLocation.sortedBy { -it.lastUpdated }
    }
}