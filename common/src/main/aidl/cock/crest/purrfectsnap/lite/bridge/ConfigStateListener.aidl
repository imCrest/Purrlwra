package cock.crest.purrfectsnap.lite.bridge;

oneway interface ConfigStateListener {
    void onConfigChanged();
    void onRestartRequired();
    void onCleanCacheRequired();
}