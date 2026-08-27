package com.willykez.files.domain

import com.willykez.files.data.model.CommandType

/**
 * Turns a free-text request ("clean up my whatsapp junk", "panga picha za skrini") into a
 * [CommandType]. Runs entirely offline — this is the primary path; [GeminiClient] is only an
 * optional enhancement layered on top by the ViewModel when a key is configured.
 */
object CommandParser {

    private val rules: List<Pair<CommandType, List<String>>> = listOf(
        CommandType.ORGANIZE_BY_TYPE to listOf("organize by type", "sort by type", "panga kwa aina", "group by type"),
        CommandType.ORGANIZE_BY_DATE to listOf("organize by date", "sort by date", "panga kwa tarehe", "group by date", "arrange by date", "year month"),
        CommandType.ORGANIZE_DOWNLOADS_BY_DATE to listOf("downloads by date", "organize downloads", "panga downloads"),
        CommandType.ORGANIZE_MEDIA_BY_SOURCE to listOf("media by source", "group media", "camera whatsapp", "panga media"),
        CommandType.ORGANIZE_CODE_PROJECTS to listOf("code projects", "organize projects", "programming projects", "panga miradi"),
        CommandType.ORGANIZE_SCREEN_RECORDINGS to listOf("screen recordings", "organize recordings", "video recordings"),

        CommandType.MOVE_SCREENSHOTS to listOf("move screenshots", "screenshots", "hamisha screenshots", "picha za skrini"),
        CommandType.MOVE_WHATSAPP_IMAGES to listOf("whatsapp images", "whatsapp picha", "move whatsapp photos"),
        CommandType.MOVE_WHATSAPP_VIDEOS to listOf("whatsapp videos", "whatsapp video", "hamisha video za whatsapp"),
        CommandType.MOVE_TELEGRAM_MEDIA to listOf("telegram media", "telegram files", "telegram downloads", "sort telegram", "telegram by type"),
        CommandType.MOVE_DOWNLOADS to listOf("move downloads", "downloads folder", "hamisha downloads"),
        CommandType.MOVE_LARGE_FILES to listOf("large files", "files over 50", "big files", "faili kubwa", "move large"),
        CommandType.MOVE_OLD_FILES to listOf("old files", "files not modified", "stale files", "faili za zamani"),
        CommandType.MOVE_AUDIO to listOf("move audio", "audio files", "music files", "sauti", "nyimbo"),
        CommandType.MOVE_VIDEOS to listOf("move videos", "all videos", "video files", "hamisha video"),
        CommandType.MOVE_DOCUMENTS to listOf("move documents", "documents", "pdf", "office files", "nyaraka"),
        CommandType.MOVE_IMAGES to listOf("move images", "all images", "photos", "hamisha picha"),
        CommandType.MOVE_MEDIA_TO_SDCARD to listOf("sd card", "external storage", "hamisha kwa sd", "move media to sd"),
        CommandType.MOVE_CAMERA_TO_SDCARD to listOf("camera to sd", "dcim to sd", "camera roll sd"),
        CommandType.OFFLOAD_LARGE_VIDEOS to listOf("large videos sd", "offload videos", "videos over 500"),
        CommandType.MOVE_ARCHIVES_TO_EXTERNAL to listOf("archives external", "zip external", "rar sd card", "7z"),

        CommandType.DELETE_DUPLICATES to listOf("delete duplicates", "duplicate files", "remove duplicates", "find duplicates", "marudio"),
        CommandType.DELETE_EMPTY_FOLDERS to listOf("empty folders", "delete empty", "remove empty folders", "folda tupu"),
        CommandType.DELETE_APKS to listOf("delete apk", "apk files", "remove apk", "installers"),
        CommandType.DELETE_UNUSED_APKS to listOf("unused apks", "delete unused", "remove unused apks"),
        CommandType.DELETE_THUMBNAILS to listOf("thumbnails", "delete thumbnails", "clear thumbnails", "picha ndogo"),
        CommandType.DELETE_CRASH_LOGS to listOf("crash logs", "error logs", "logcat", "crash reports"),

        CommandType.CLEAN_WHATSAPP_SENT_JUNK to listOf("whatsapp sent", "clean whatsapp", "whatsapp junk", "whatsapp duplicate"),
        CommandType.CLEAN_TIKTOK_CACHE to listOf("tiktok cache", "clean tiktok", "tiktok files"),
        CommandType.CLEAN_INSTAGRAM_CACHE to listOf("instagram cache", "clean instagram", "instagram files", "reels cache"),

        CommandType.CLEAN_GRADLE_CACHE to listOf("gradle cache", "clean gradle", ".gradle"),
        CommandType.REMOVE_NODE_MODULES to listOf("node_modules", "node modules", "npm cache"),
        CommandType.CLEAN_BUILD_OUTPUTS to listOf("build outputs", "build folders", "/build", "/dist"),

        // Kept deliberately generic and placed after every specific *_CACHE rule above, so a
        // request naming a specific app/tool (Gradle, TikTok, Instagram, WhatsApp) is matched to
        // that command rather than being swallowed by the bare word "cache" here.
        CommandType.DELETE_TEMP_FILES to listOf("temp files", "temporary files", "tmp files", "delete cache", "clear cache", "futa temp", "faili za muda", "futa kache"),

        CommandType.FIND_LARGEST_FILES to listOf("largest files", "biggest files", "top files by size", "find large", "faili kubwa zaidi"),
        CommandType.FIND_UNUSED_FILES to listOf("unused files", "find unused", "not accessed", "old files analysis"),
        CommandType.ANALYZE_STORAGE_USAGE to listOf("storage usage", "analyze storage", "storage breakdown", "uchambuzi wa hifadhi"),

        CommandType.AUTO_ORGANIZE_DAILY to listOf("daily organize", "auto organize", "schedule organize", "nightly organize"),
        CommandType.NIGHTLY_CLEANUP to listOf("nightly cleanup", "nightly clean", "auto cleanup", "schedule cleanup", "usafishaji usiku")
    )

    fun matchOffline(userInput: String): CommandType {
        val input = userInput.lowercase().trim()
        for ((command, keywords) in rules) {
            if (keywords.any { input.contains(it) }) return command
        }
        return CommandType.UNKNOWN
    }

    fun looksLikeCommand(msg: String): Boolean {
        val l = msg.lowercase()
        return listOf("organize", "move", "delete", "clean", "remove", "sort", "panga", "futa", "hamisha").any { l.contains(it) }
    }
}
