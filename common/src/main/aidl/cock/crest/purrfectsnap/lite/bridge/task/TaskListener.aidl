package cock.crest.purrfectsnap.lite.bridge.task;

interface TaskListener {
    oneway void onProgress(String label, int progress);
    oneway void onStateChange(String status);
    oneway void onSuccess();
    oneway void onCancel();
}

