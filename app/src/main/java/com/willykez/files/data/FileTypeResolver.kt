package com.willykez.files.data

import com.willykez.files.data.model.FileMetadata
import java.util.Locale

object FileTypeResolver {

    const val IMAGE = "Images"
    const val VIDEO = "Videos"
    const val AUDIO = "Audio"
    const val DOCUMENT = "Documents"
    const val APK = "APKs"
    const val ARCHIVE = "Archives"
    const val CODE = "Code"
    const val OTHER = "Other"

    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "svg")
    private val VIDEO_EXT = setOf("mp4", "mkv", "avi", "mov", "flv", "wmv", "3gp")
    private val AUDIO_EXT = setOf("mp3", "wav", "aac", "flac", "ogg", "wma", "m4a", "opus")
    private val DOCUMENT_EXT = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "odt", "ods", "odp"
    )
    private val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
    private val CODE_EXT = setOf(
        "java", "kt", "py", "js", "ts", "html", "css", "xml", "gradle", "json", "sh",
        "c", "cpp", "h", "swift", "go"
    )
    private val TEMP_EXT = setOf("tmp", "temp", "cache", "bak")

    fun resolveCategory(extension: String): String {
        val ext = extension.lowercase(Locale.ROOT)
        return when {
            ext.isEmpty() -> OTHER
            ext == "apk" -> APK
            ext in IMAGE_EXT -> IMAGE
            ext in VIDEO_EXT -> VIDEO
            ext in AUDIO_EXT -> AUDIO
            ext in DOCUMENT_EXT -> DOCUMENT
            ext in ARCHIVE_EXT -> ARCHIVE
            ext in CODE_EXT -> CODE
            else -> OTHER
        }
    }

    fun isImage(ext: String) = resolveCategory(ext) == IMAGE
    fun isVideo(ext: String) = resolveCategory(ext) == VIDEO
    fun isAudio(ext: String) = resolveCategory(ext) == AUDIO
    fun isDocument(ext: String) = resolveCategory(ext) == DOCUMENT
    fun isApk(ext: String) = resolveCategory(ext) == APK
    fun isArchive(ext: String) = resolveCategory(ext) == ARCHIVE
    fun isTempFile(ext: String) = ext.lowercase(Locale.ROOT) in TEMP_EXT

    private fun FileMetadata.parentLower() = parentPath.lowercase(Locale.ROOT)
    private fun FileMetadata.nameLower() = name.lowercase(Locale.ROOT)

    fun isScreenshot(meta: FileMetadata) =
        meta.nameLower().contains("screenshot") || meta.parentLower().contains("screenshots")

    fun isWhatsAppImage(meta: FileMetadata) =
        meta.parentLower().let { it.contains("whatsapp") && it.contains("images") } && isImage(meta.extension)

    fun isWhatsAppVideo(meta: FileMetadata) =
        meta.parentLower().let { it.contains("whatsapp") && it.contains("video") } && isVideo(meta.extension)

    fun isTelegramMedia(meta: FileMetadata) = meta.parentLower().contains("telegram")

    fun isCameraFile(meta: FileMetadata) =
        meta.parentLower().let { it.contains("dcim") || it.contains("/camera") }

    fun isDownload(meta: FileMetadata) = meta.parentLower().contains("/download")

    fun isThumbnail(meta: FileMetadata) = meta.parentLower().contains("thumbnails")

    fun isCrashLog(meta: FileMetadata): Boolean {
        val name = meta.nameLower()
        val ext = meta.extension.lowercase(Locale.ROOT)
        return name.contains("crash") || name.contains("anr") || name.contains("logcat") ||
            ext == "log" || ext == "trace"
    }

    fun isScreenRecording(meta: FileMetadata): Boolean {
        val name = meta.nameLower()
        return isVideo(meta.extension) &&
            (name.contains("screen_record") || name.contains("screenrecord") || meta.parentLower().contains("screen_record"))
    }

    fun isCodeProjectRoot(files: List<String>): Boolean =
        "build.gradle" in files || "package.json" in files || "pom.xml" in files || ".git" in files
}
