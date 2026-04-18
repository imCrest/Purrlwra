package cock.crest.purrfectsnap.lite.common.config.impl

import cock.crest.purrfectsnap.lite.common.config.ConfigContainer
import cock.crest.purrfectsnap.lite.common.config.ConfigFlag
import cock.crest.purrfectsnap.lite.common.config.FeatureNotice

class DownloaderConfig : ConfigContainer() {
    inner class FFMpegOptions : ConfigContainer() {
        val threads = integer("threads", 4) // Bump Default Value to 4 Tested on Pixel 5 (Qualcomm Snapdragon 765G) Had no lag
        val preset = unique("preset", "ultrafast", "superfast", "veryfast", "faster", "fast", "medium", "slow", "slower", "veryslow") {
            addFlags(ConfigFlag.NO_TRANSLATE)
        }.apply { set("veryfast") }
        val constantRateFactor = integer("constant_rate_factor", 22)
        val videoBitrate = integer("video_bitrate", 8000)
        val audioBitrate = integer("audio_bitrate", 128)
        val customVideoCodec = string("custom_video_codec") { addFlags(ConfigFlag.NO_TRANSLATE) }
        val customAudioCodec = string("custom_audio_codec") { addFlags(ConfigFlag.NO_TRANSLATE) }
    }

    val saveFolder = string("save_folder") { addFlags(ConfigFlag.FOLDER, ConfigFlag.SENSITIVE); requireRestart() }
    val autoDownloadSources = multiple("auto_download_sources",
        "friend_snaps",
        "friend_stories",
        "public_stories",
        "spotlight"
    )
    val preventSelfAutoDownload = boolean("prevent_self_auto_download")
    val pathFormat = multiple("path_format",
        "create_author_folder",
        "create_source_folder",
        "append_hash",
        "append_source",
        "append_username",
        "append_date_time",
    ).apply { set(mutableListOf("append_hash", "append_date_time", "append_type", "append_username")) }
    val allowDuplicate = boolean("allow_duplicate")
    val mergeOverlays = boolean("merge_overlays") { addNotices(FeatureNotice.UNSTABLE) }
    val forceImageFormat = unique("force_image_format", "jpg", "png", "webp") {
        addFlags(ConfigFlag.NO_TRANSLATE)
    }
    val forceVoiceNoteFormat = unique("force_voice_note_format", "aac", "mp3", "opus") {
        addFlags(ConfigFlag.NO_TRANSLATE)
    }
    val autoDownloadVoiceNotes = boolean("auto_download_voice_notes") { requireRestart(); addNotices(FeatureNotice.UNSTABLE) }
    val downloadProfilePictures = boolean("download_profile_pictures") { requireRestart() }
    val operaDownloadButton = boolean("opera_download_button") { requireRestart() }
    val storySnapListDownload = boolean("story_snap_list_download", true)
    val downloadContextMenu = boolean("download_context_menu")
    val ffmpegOptions = container("ffmpeg_options", FFMpegOptions()) { addNotices(FeatureNotice.UNSTABLE) }
    val logging = multiple("logging", "started", "success", "progress", "failure").apply {
        set(mutableListOf("success", "progress", "failure"))
    }
    val customPathFormat = string("custom_path_format") { addNotices(FeatureNotice.UNSTABLE) }
    val fileHashCheck = boolean("file_hash_check")
    inner class CallRecorderOptions : ConfigContainer() {
        val callRecorder = unique("call_recorder", "only_record_self", "only_record_others", "record_both") { addNotices(FeatureNotice.UNSTABLE) }
        val autoStartRecording = boolean("auto_start_recording", false)
        val callRecorderUi = boolean("call_recorder_ui", true)
        val callRecorderUiDesign = unique("call_recorder_ui_design", "default", "snapchat", "cyber", "frost") {
            addFlags(ConfigFlag.NO_DISABLE_KEY)
        }.apply { set("default") }
    }
    val callRecorder = container("call_recorder", CallRecorderOptions()) { requireRestart() }
    val chatWallpaperDownloader = boolean("chat_wallpaper_downloader") { requireRestart() }
}
