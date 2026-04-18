package cock.crest.purrfectsnap.lite.bridge.storage;

import cock.crest.purrfectsnap.lite.bridge.storage.FileHandle;

interface FileHandleManager {
    @nullable FileHandle getFileHandle(String scope, String name);
}