package cock.crest.purrfectsnap.lite.bridge.location;

import cock.crest.purrfectsnap.lite.bridge.location.FriendLocation;

interface LocationManager {
    void provideFriendsLocation(in List<FriendLocation> friendsLocation);
}