package com.willykez.files.domain

import com.willykez.files.data.FileTypeResolver
import com.willykez.files.data.model.CommandType
import com.willykez.files.data.model.FileMetadata

private const val LARGE_FILE_BYTES = 50L * 1024 * 1024
private const val LARGE_VIDEO_BYTES = 500L * 1024 * 1024
private const val OLD_FILE_MILLIS = 180L * 24 * 60 * 60 * 1000

/**
 * Predicate for "does this file belong to this command". Used to build both the live file-preview
 * list before running a command and the actual selection the executor acts on, so the two can
 * never drift out of sync.
 *
 * A handful of commands (organize-by-type, organize-by-date, code-project detection) don't reduce
 * to a per-file predicate — they're handled directly in [CommandExecutor] and return `true` here
 * only where a simple preview makes sense.
 */
object CommandMatcher {

    fun matches(command: CommandType, meta: FileMetadata): Boolean = when (command) {
        CommandType.ORGANIZE_BY_TYPE, CommandType.ORGANIZE_BY_DATE -> true
        CommandType.ORGANIZE_DOWNLOADS_BY_DATE -> FileTypeResolver.isDownload(meta)
        CommandType.ORGANIZE_MEDIA_BY_SOURCE ->
            FileTypeResolver.isScreenshot(meta) || FileTypeResolver.isWhatsAppImage(meta) ||
                FileTypeResolver.isWhatsAppVideo(meta) || FileTypeResolver.isTelegramMedia(meta) ||
                FileTypeResolver.isCameraFile(meta)
        CommandType.ORGANIZE_SCREEN_RECORDINGS -> FileTypeResolver.isScreenRecording(meta)

        CommandType.MOVE_SCREENSHOTS -> FileTypeResolver.isScreenshot(meta)
        CommandType.MOVE_WHATSAPP_IMAGES -> FileTypeResolver.isWhatsAppImage(meta)
        CommandType.MOVE_WHATSAPP_VIDEOS -> FileTypeResolver.isWhatsAppVideo(meta)
        CommandType.MOVE_TELEGRAM_MEDIA -> FileTypeResolver.isTelegramMedia(meta)
        CommandType.MOVE_DOWNLOADS -> FileTypeResolver.isDownload(meta)
        CommandType.MOVE_LARGE_FILES -> meta.sizeBytes > LARGE_FILE_BYTES
        CommandType.MOVE_OLD_FILES -> System.currentTimeMillis() - meta.lastModified > OLD_FILE_MILLIS
        CommandType.MOVE_AUDIO -> FileTypeResolver.isAudio(meta.extension)
        CommandType.MOVE_VIDEOS -> FileTypeResolver.isVideo(meta.extension)
        CommandType.MOVE_DOCUMENTS -> FileTypeResolver.isDocument(meta.extension)
        CommandType.MOVE_IMAGES -> FileTypeResolver.isImage(meta.extension)
        CommandType.MOVE_MEDIA_TO_SDCARD -> FileTypeResolver.isImage(meta.extension) || FileTypeResolver.isVideo(meta.extension)
        CommandType.MOVE_CAMERA_TO_SDCARD -> FileTypeResolver.isCameraFile(meta)
        CommandType.OFFLOAD_LARGE_VIDEOS -> FileTypeResolver.isVideo(meta.extension) && meta.sizeBytes > LARGE_VIDEO_BYTES
        CommandType.MOVE_ARCHIVES_TO_EXTERNAL -> FileTypeResolver.isArchive(meta.extension)

        CommandType.DELETE_DUPLICATES -> true // resolved by hash grouping, not a per-file predicate
        CommandType.DELETE_EMPTY_FOLDERS -> false // directory-level, not file-level
        CommandType.DELETE_TEMP_FILES -> FileTypeResolver.isTempFile(meta.extension)
        CommandType.DELETE_APKS, CommandType.DELETE_UNUSED_APKS -> FileTypeResolver.isApk(meta.extension)
        CommandType.DELETE_THUMBNAILS -> FileTypeResolver.isThumbnail(meta)
        CommandType.DELETE_CRASH_LOGS -> FileTypeResolver.isCrashLog(meta)

        CommandType.CLEAN_WHATSAPP_SENT_JUNK ->
            meta.parentPath.lowercase().let { it.contains("whatsapp") && it.contains("sent") }

        CommandType.FIND_LARGEST_FILES, CommandType.FIND_UNUSED_FILES, CommandType.ANALYZE_STORAGE_USAGE -> true

        else -> false
    }

    fun previewCount(command: CommandType, metadata: List<FileMetadata>, limit: Int = 200): List<FileMetadata> =
        metadata.asSequence().filter { matches(command, it) }.take(limit).toList()
}
