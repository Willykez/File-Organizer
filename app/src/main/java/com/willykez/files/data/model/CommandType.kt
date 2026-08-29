package com.willykez.files.data.model

enum class OperationKind { MOVE, DELETE, ORGANIZE, OTHER }

enum class Cost { LOW, MEDIUM, HIGH }

enum class Category(val label: String) {
    ORGANIZATION("Organize"),
    MOVE("Move & Relocate"),
    CLEANUP("Clean Up"),
    SOCIAL("Social Media"),
    DEVELOPER("Developer Tools"),
    ANALYSIS("Storage Analysis"),
    AUTOMATION("Automation")
}

/**
 * Every supported operation. Two commands from the original catalog were removed here because
 * they executed byte-for-byte identical logic to a sibling command (dead duplication, not real
 * functionality): `MOVE_TELEGRAM_MEDIA_ALT` was identical to [MOVE_TELEGRAM_MEDIA], and
 * `DELETE_CACHE` was identical to [DELETE_TEMP_FILES].
 */
enum class CommandType(
    val displayName: String,
    val category: Category,
    val description: String,
    val emoji: String,
    val kind: OperationKind,
    val estimatedCost: Cost
) {
    UNKNOWN("Unknown", Category.ANALYSIS, "Unknown operation", "❓", OperationKind.OTHER, Cost.LOW),

    ORGANIZE_BY_TYPE(
        "Organize by File Type", Category.ORGANIZATION,
        "Sort every file into typed sub-folders (Images, Videos, Audio, Documents…)",
        "📂", OperationKind.ORGANIZE, Cost.HIGH
    ),
    ORGANIZE_BY_DATE(
        "Organize by Date", Category.ORGANIZATION,
        "Sort files into year/month folders based on last-modified date",
        "📅", OperationKind.ORGANIZE, Cost.HIGH
    ),
    ORGANIZE_DOWNLOADS_BY_DATE(
        "Organize Downloads by Date", Category.ORGANIZATION,
        "Sort the Downloads folder into dated sub-folders",
        "📥", OperationKind.ORGANIZE, Cost.MEDIUM
    ),
    ORGANIZE_MEDIA_BY_SOURCE(
        "Organize Media by Source", Category.ORGANIZATION,
        "Group media into Camera, WhatsApp, Telegram, Screenshots sub-folders",
        "🖼️", OperationKind.ORGANIZE, Cost.HIGH
    ),
    ORGANIZE_CODE_PROJECTS(
        "Organize Code Projects", Category.ORGANIZATION,
        "Move detected code project folders into a unified Projects directory",
        "💻", OperationKind.ORGANIZE, Cost.MEDIUM
    ),
    ORGANIZE_SCREEN_RECORDINGS(
        "Organize Screen Recordings", Category.ORGANIZATION,
        "Consolidate all screen recordings into Videos/ScreenRecordings",
        "🎥", OperationKind.ORGANIZE, Cost.LOW
    ),

    MOVE_SCREENSHOTS(
        "Move Screenshots", Category.MOVE,
        "Consolidate all screenshots into Organized/Screenshots",
        "📸", OperationKind.MOVE, Cost.LOW
    ),
    MOVE_WHATSAPP_IMAGES(
        "Move WhatsApp Images", Category.MOVE,
        "Gather WhatsApp received images into one folder",
        "💬", OperationKind.MOVE, Cost.LOW
    ),
    MOVE_WHATSAPP_VIDEOS(
        "Move WhatsApp Videos", Category.MOVE,
        "Gather WhatsApp received videos into one folder",
        "🎬", OperationKind.MOVE, Cost.LOW
    ),
    MOVE_TELEGRAM_MEDIA(
        "Move Telegram Media", Category.MOVE,
        "Consolidate Telegram downloads (images, videos, docs) into sub-folders by type",
        "✈️", OperationKind.MOVE, Cost.MEDIUM
    ),
    MOVE_DOWNLOADS(
        "Move Downloads", Category.MOVE,
        "Move all files from the Downloads folder to Organized/Downloads",
        "📦", OperationKind.MOVE, Cost.MEDIUM
    ),
    MOVE_LARGE_FILES(
        "Move Large Files (>50 MB)", Category.MOVE,
        "Move files over 50 MB into Organized/LargeFiles",
        "🐘", OperationKind.MOVE, Cost.MEDIUM
    ),
    MOVE_OLD_FILES(
        "Move Old Files (>180 days)", Category.MOVE,
        "Move files unmodified for over 180 days into Organized/OldFiles",
        "🕰️", OperationKind.MOVE, Cost.HIGH
    ),
    MOVE_AUDIO(
        "Move Audio Files", Category.MOVE,
        "Collect all audio files into Organized/Audio",
        "🎵", OperationKind.MOVE, Cost.MEDIUM
    ),
    MOVE_VIDEOS(
        "Move Videos", Category.MOVE,
        "Collect all video files into Organized/Videos",
        "🎞️", OperationKind.MOVE, Cost.MEDIUM
    ),
    MOVE_DOCUMENTS(
        "Move Documents", Category.MOVE,
        "Collect all documents (PDF, Office, text) into Organized/Documents",
        "📄", OperationKind.MOVE, Cost.MEDIUM
    ),
    MOVE_IMAGES(
        "Move Images", Category.MOVE,
        "Collect all image files into Organized/Images",
        "🖼️", OperationKind.MOVE, Cost.MEDIUM
    ),
    MOVE_MEDIA_TO_SDCARD(
        "Move Media to SD Card", Category.MOVE,
        "Transfer photos and videos from internal storage to the SD card",
        "💾", OperationKind.MOVE, Cost.HIGH
    ),
    MOVE_CAMERA_TO_SDCARD(
        "Move Camera Roll to SD Card", Category.MOVE,
        "Transfer DCIM/Camera files to the SD card",
        "📸", OperationKind.MOVE, Cost.HIGH
    ),
    OFFLOAD_LARGE_VIDEOS(
        "Offload Large Videos to SD Card", Category.MOVE,
        "Move videos over 500 MB from internal storage to SD card",
        "📽️", OperationKind.MOVE, Cost.HIGH
    ),
    MOVE_ARCHIVES_TO_EXTERNAL(
        "Move Archives to External Storage", Category.MOVE,
        "Transfer zip/rar/7z archives to SD card or USB storage",
        "🗜️", OperationKind.MOVE, Cost.MEDIUM
    ),

    DELETE_DUPLICATES(
        "Delete Duplicates", Category.CLEANUP,
        "Find and remove duplicate files (verified by SHA-256 hash)",
        "👯", OperationKind.DELETE, Cost.HIGH
    ),
    DELETE_EMPTY_FOLDERS(
        "Delete Empty Folders", Category.CLEANUP,
        "Remove all empty directories from storage",
        "🗑️", OperationKind.DELETE, Cost.LOW
    ),
    DELETE_TEMP_FILES(
        "Delete Temporary & Cache Files", Category.CLEANUP,
        "Remove .tmp, .temp, .cache, and .bak files from all storage volumes",
        "🧹", OperationKind.DELETE, Cost.MEDIUM
    ),
    DELETE_APKS(
        "Delete APK Files", Category.CLEANUP,
        "Remove APK installer files from Downloads and other folders",
        "🤖", OperationKind.DELETE, Cost.LOW
    ),
    DELETE_THUMBNAILS(
        "Delete Thumbnail Caches", Category.CLEANUP,
        "Clear .thumbnails folders to reclaim space",
        "🖼️", OperationKind.DELETE, Cost.LOW
    ),
    DELETE_CRASH_LOGS(
        "Delete Crash Logs", Category.CLEANUP,
        "Remove crash dump, ANR trace, and logcat files from external storage",
        "🚨", OperationKind.DELETE, Cost.LOW
    ),
    DELETE_UNUSED_APKS(
        "Delete Unused APKs", Category.CLEANUP,
        "Remove APKs whose package is already installed on the device",
        "📦", OperationKind.DELETE, Cost.MEDIUM
    ),

    CLEAN_WHATSAPP_SENT_JUNK(
        "Clean WhatsApp Sent Folder", Category.SOCIAL,
        "Remove already-sent WhatsApp media that duplicates your originals",
        "📤", OperationKind.DELETE, Cost.MEDIUM
    ),
    CLEAN_TIKTOK_CACHE(
        "Clean TikTok Cache", Category.SOCIAL,
        "Remove TikTok app cache and draft leftovers from Android/data",
        "🎵", OperationKind.DELETE, Cost.LOW
    ),
    CLEAN_INSTAGRAM_CACHE(
        "Clean Instagram Cache", Category.SOCIAL,
        "Remove Instagram temporary files and cached reels",
        "📸", OperationKind.DELETE, Cost.LOW
    ),

    CLEAN_GRADLE_CACHE(
        "Clean Gradle Cache", Category.DEVELOPER,
        "Remove the Gradle build cache (.gradle folder) from internal storage",
        "🔨", OperationKind.DELETE, Cost.MEDIUM
    ),
    REMOVE_NODE_MODULES(
        "Remove node_modules Folders", Category.DEVELOPER,
        "Find and delete all node_modules directories (can be very large)",
        "📦", OperationKind.DELETE, Cost.HIGH
    ),
    CLEAN_BUILD_OUTPUTS(
        "Clean Build Output Folders", Category.DEVELOPER,
        "Remove /build, /dist, /out directories inside code project folders",
        "🏗️", OperationKind.DELETE, Cost.MEDIUM
    ),

    FIND_LARGEST_FILES(
        "Find Largest Files", Category.ANALYSIS,
        "Identify the top 100 largest files across all active storage",
        "🔍", OperationKind.OTHER, Cost.HIGH
    ),
    FIND_UNUSED_FILES(
        "Find Unused Files", Category.ANALYSIS,
        "Identify files not modified in over a year",
        "🕸️", OperationKind.OTHER, Cost.HIGH
    ),
    ANALYZE_STORAGE_USAGE(
        "Analyze Storage Usage", Category.ANALYSIS,
        "Generate a breakdown of storage consumption by file type and folder",
        "📊", OperationKind.OTHER, Cost.HIGH
    ),

    AUTO_ORGANIZE_DAILY(
        "Daily Auto-Organize", Category.AUTOMATION,
        "Schedule automatic nightly organization of Downloads and Screenshots",
        "🤖", OperationKind.ORGANIZE, Cost.HIGH
    ),
    NIGHTLY_CLEANUP(
        "Nightly Cleanup", Category.AUTOMATION,
        "Schedule automatic deletion of temp files and empty folders each night",
        "🌙", OperationKind.DELETE, Cost.MEDIUM
    );

    companion object {
        fun byCategory(category: Category): List<CommandType> = entries.filter { it.category == category }

        /** Commands safe (and meaningful) to run unattended on a schedule via WorkManager. */
        val automatable: Set<CommandType> = setOf(AUTO_ORGANIZE_DAILY, NIGHTLY_CLEANUP)

        /**
         * Commands that deliberately reach inside a protected (project/firmware) folder rather
         * than being blocked by [com.willykez.files.domain.ProtectionRules] — developer cleanup
         * tools that target known-regenerable subfolders (node_modules, build output, Gradle
         * cache), the command that moves whole project folders intact, and read-only analysis.
         * Everything else — organize/move/delete commands that act on individual files by type,
         * date, or size — respects protected folders by default.
         */
        val protectionExempt: Set<CommandType> = setOf(
            UNKNOWN, CLEAN_TIKTOK_CACHE, CLEAN_INSTAGRAM_CACHE, CLEAN_GRADLE_CACHE,
            REMOVE_NODE_MODULES, CLEAN_BUILD_OUTPUTS, ORGANIZE_CODE_PROJECTS,
            FIND_LARGEST_FILES, FIND_UNUSED_FILES, ANALYZE_STORAGE_USAGE,
            AUTO_ORGANIZE_DAILY, NIGHTLY_CLEANUP
        )
    }
}
