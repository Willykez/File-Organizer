package com.willykez.files.domain

import android.content.Context
import android.content.pm.PackageManager
import com.willykez.files.data.FileTypeResolver
import com.willykez.files.data.model.ActionStatus
import com.willykez.files.data.model.CommandType
import com.willykez.files.data.model.ExecutionResult
import com.willykez.files.data.model.FileMetadata
import com.willykez.files.data.model.UndoableMove
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Calendar

/**
 * Runs commands against scanned metadata, across every detected storage volume — not just
 * internal storage. Two design choices worth calling out:
 *
 * 1. Cleanup commands that used to hardcode `Environment.getExternalStorageDirectory()` (empty
 *    folders, Gradle cache, app caches) now loop over every volume from [StorageVolumeManager],
 *    so an SD card is cleaned exactly like internal storage instead of being silently skipped.
 * 2. "Organize"/"Move" commands keep each file on the *same* volume it started on — a screenshot
 *    living on the SD card ends up in `<sdcard>/Organized/Screenshots`, not copied over to
 *    internal storage. This preserves the user's storage layout (and avoids surprising a nearly-
 *    full internal storage with a flood of incoming copies). The explicit "…to SD Card" commands
 *    are the one deliberate exception — those exist specifically to move files *onto* an SD card.
 */
class CommandExecutor(private val context: Context) {

    private val volumeManager = StorageVolumeManager(context)

    suspend fun execute(command: CommandType, metadata: List<FileMetadata>, volumes: List<StorageVolume>? = null): ExecutionResult =
        withContext(Dispatchers.IO) {
            val result = ExecutionResult(command)
            val resolvedVolumes = volumes ?: volumeManager.listVolumes()
            when (command) {
                CommandType.ORGANIZE_BY_TYPE -> organizeByType(metadata, result, resolvedVolumes)
                CommandType.ORGANIZE_BY_DATE -> organizeByDate(metadata, result, resolvedVolumes)
                CommandType.ORGANIZE_DOWNLOADS_BY_DATE -> organizeDownloadsByDate(metadata, result, resolvedVolumes)
                CommandType.ORGANIZE_MEDIA_BY_SOURCE -> organizeMediaBySource(metadata, result, resolvedVolumes)
                CommandType.ORGANIZE_CODE_PROJECTS -> organizeCodeProjects(metadata, result, resolvedVolumes)
                CommandType.ORGANIZE_SCREEN_RECORDINGS ->
                    moveMatching(metadata, result, command, resolvedVolumes, "Videos/ScreenRecordings", "Move screen recording")

                CommandType.MOVE_SCREENSHOTS -> moveMatching(metadata, result, command, resolvedVolumes, "Screenshots", "Move screenshot")
                CommandType.MOVE_WHATSAPP_IMAGES -> moveMatching(metadata, result, command, resolvedVolumes, "WhatsApp/Images", "Move WhatsApp image")
                CommandType.MOVE_WHATSAPP_VIDEOS -> moveMatching(metadata, result, command, resolvedVolumes, "WhatsApp/Videos", "Move WhatsApp video")
                CommandType.MOVE_TELEGRAM_MEDIA -> moveTelegramMedia(metadata, result, resolvedVolumes)
                CommandType.MOVE_DOWNLOADS -> moveMatching(metadata, result, command, resolvedVolumes, "Downloads", "Move download")
                CommandType.MOVE_LARGE_FILES -> moveMatching(metadata, result, command, resolvedVolumes, "LargeFiles", "Move large file")
                CommandType.MOVE_OLD_FILES -> moveMatching(metadata, result, command, resolvedVolumes, "OldFiles", "Move old file")
                CommandType.MOVE_AUDIO -> moveMatching(metadata, result, command, resolvedVolumes, FileTypeResolver.AUDIO, "Move audio")
                CommandType.MOVE_VIDEOS -> moveMatching(metadata, result, command, resolvedVolumes, FileTypeResolver.VIDEO, "Move video")
                CommandType.MOVE_DOCUMENTS -> moveMatching(metadata, result, command, resolvedVolumes, FileTypeResolver.DOCUMENT, "Move document")
                CommandType.MOVE_IMAGES -> moveMatching(metadata, result, command, resolvedVolumes, FileTypeResolver.IMAGE, "Move image")
                CommandType.MOVE_MEDIA_TO_SDCARD -> moveToSdCard(metadata, result, command, "Organized/Media", "Move media to SD")
                CommandType.MOVE_CAMERA_TO_SDCARD -> moveToSdCard(metadata, result, command, "DCIM/Camera", "Move camera file to SD")
                CommandType.OFFLOAD_LARGE_VIDEOS -> moveToSdCard(metadata, result, command, "Organized/LargeVideos", "Offload large video")
                CommandType.MOVE_ARCHIVES_TO_EXTERNAL -> moveToSdCard(metadata, result, command, "Organized/Archives", "Move archive to SD")

                CommandType.DELETE_DUPLICATES -> deleteDuplicates(metadata, result)
                CommandType.DELETE_EMPTY_FOLDERS -> deleteEmptyFolders(result, resolvedVolumes)
                CommandType.DELETE_TEMP_FILES -> deleteMatching(metadata, result, command, "Delete temp file")
                CommandType.DELETE_APKS -> deleteApks(metadata, result, unusedOnly = false)
                CommandType.DELETE_UNUSED_APKS -> deleteApks(metadata, result, unusedOnly = true)
                CommandType.DELETE_THUMBNAILS -> deleteMatching(metadata, result, command, "Delete thumbnail")
                CommandType.DELETE_CRASH_LOGS -> deleteMatching(metadata, result, command, "Delete crash log")

                CommandType.CLEAN_WHATSAPP_SENT_JUNK -> deleteMatching(metadata, result, command, "Clean WhatsApp sent")
                CommandType.CLEAN_TIKTOK_CACHE -> cleanAppCache(result, resolvedVolumes, "com.zhiliaoapp.musically", "TikTok")
                CommandType.CLEAN_INSTAGRAM_CACHE -> cleanAppCache(result, resolvedVolumes, "com.instagram.android", "Instagram")
                CommandType.CLEAN_GRADLE_CACHE -> cleanGradleCache(result, resolvedVolumes)
                CommandType.REMOVE_NODE_MODULES -> removeNodeModules(metadata, result)
                CommandType.CLEAN_BUILD_OUTPUTS -> cleanBuildOutputs(metadata, result)

                CommandType.FIND_LARGEST_FILES -> findLargestFiles(metadata, result)
                CommandType.FIND_UNUSED_FILES -> findUnusedFiles(metadata, result)
                CommandType.ANALYZE_STORAGE_USAGE -> analyzeStorage(metadata, result, resolvedVolumes)

                else -> result.add(ActionStatus.SKIPPED, "-", "Not implemented", command.displayName)
            }
            result
        }

    /**
     * Reverses every move recorded in [result]'s undo log, moving files back to their original
     * location. New in this rewrite — the original app had no way to undo a batch operation.
     * Deletes are not reversible and are simply skipped.
     */
    suspend fun undo(result: ExecutionResult): ExecutionResult = withContext(Dispatchers.IO) {
        val undoResult = ExecutionResult(result.command)
        for (action in result.actions) {
            val move = action.undo ?: continue
            currentCoroutineContext().ensureActive()
            val current = File(move.movedTo)
            val original = File(move.movedFrom)
            if (!current.exists()) {
                undoResult.add(ActionStatus.SKIPPED, current.name, "Undo move", "File no longer at destination")
                continue
            }
            try {
                original.parentFile?.mkdirs()
                val restoreTo = resolveConflict(original)
                copyFile(current, restoreTo)
                if (current.delete()) {
                    undoResult.add(ActionStatus.SUCCESS, current.name, "Undo move", "→ ${restoreTo.path}")
                } else {
                    restoreTo.delete()
                    undoResult.add(ActionStatus.FAILED, current.name, "Undo move", "Could not remove copy at destination")
                }
            } catch (e: Exception) {
                undoResult.add(ActionStatus.FAILED, current.name, "Undo move", e.message ?: "Restore failed")
            }
        }
        undoResult
    }

    // ---- shared helpers -----------------------------------------------------------------

    /**
     * Resolves the "Organized/<sub>" directory on whichever volume [meta] actually lives on
     * (falling back to the primary volume if the metadata predates volume tracking, or if its
     * volume is no longer mounted — e.g. an SD card that's since been removed).
     */
    private fun organizedDirFor(meta: FileMetadata, sub: String, volumes: List<StorageVolume>): File {
        val volume = volumes.firstOrNull { it.root.absolutePath == meta.volumeRoot }
            ?: volumeManager.volumeContaining(meta.absolutePath, volumes)
            ?: volumes.firstOrNull { it.isPrimary }
            ?: volumes.firstOrNull()
        val root = volume?.root ?: File(meta.volumeRoot.ifBlank { "/storage/emulated/0" })
        return File(root, "Organized/$sub").apply { mkdirs() }
    }

    private suspend fun moveFile(src: File, destDir: File, result: ExecutionResult, action: String) {
        currentCoroutineContext().ensureActive()
        if (!src.exists()) {
            result.add(ActionStatus.SKIPPED, src.name, action, "Source not found")
            return
        }
        destDir.mkdirs()
        val dest = resolveConflict(File(destDir, src.name))
        try {
            copyFile(src, dest)
            val originalPath = src.absolutePath
            if (src.delete()) {
                result.add(
                    ActionStatus.SUCCESS, src.name, action, "→ ${destDir.path}",
                    undo = UndoableMove(movedFrom = originalPath, movedTo = dest.absolutePath)
                )
            } else {
                dest.delete()
                result.add(ActionStatus.FAILED, src.name, action, "Could not remove source after copy")
            }
        } catch (e: Exception) {
            result.add(ActionStatus.FAILED, src.name, action, e.message ?: "Copy failed")
        }
    }

    private fun copyFile(src: File, dest: File) {
        FileInputStream(src).use { fis ->
            FileOutputStream(dest).use { fos ->
                fis.channel.use { inCh -> fos.channel.use { outCh -> inCh.transferTo(0, inCh.size(), outCh) } }
            }
        }
    }

    private fun resolveConflict(dest: File): File {
        if (!dest.exists()) return dest
        val name = dest.name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        var candidate: File
        do {
            candidate = File(dest.parent, "${base}_$i$ext")
            i++
        } while (candidate.exists())
        return candidate
    }

    /** Moves every file matching [command] into "Organized/<sub>" on that file's own volume. */
    private suspend fun moveMatching(
        metadata: List<FileMetadata>, result: ExecutionResult, command: CommandType,
        volumes: List<StorageVolume>, sub: String, action: String
    ) {
        for (meta in metadata) {
            if (CommandMatcher.matches(command, meta)) {
                moveFile(File(meta.absolutePath), organizedDirFor(meta, sub, volumes), result, action)
            }
        }
    }

    private suspend fun deleteMatching(metadata: List<FileMetadata>, result: ExecutionResult, command: CommandType, action: String) {
        for (meta in metadata) {
            if (!CommandMatcher.matches(command, meta)) continue
            currentCoroutineContext().ensureActive()
            val src = File(meta.absolutePath)
            if (!src.exists()) continue
            if (src.delete()) result.add(ActionStatus.SUCCESS, meta.name, action, meta.parentPath)
            else result.add(ActionStatus.FAILED, meta.name, action, "Could not delete")
        }
    }

    // ---- organize -------------------------------------------------------------------------

    private suspend fun organizeByType(files: List<FileMetadata>, result: ExecutionResult, volumes: List<StorageVolume>) {
        for (meta in files) {
            val cat = FileTypeResolver.resolveCategory(meta.extension)
            moveFile(File(meta.absolutePath), organizedDirFor(meta, cat, volumes), result, "Organize by type")
        }
    }

    private suspend fun organizeByDate(files: List<FileMetadata>, result: ExecutionResult, volumes: List<StorageVolume>) {
        val cal = Calendar.getInstance()
        for (meta in files) {
            val src = File(meta.absolutePath)
            if (!src.exists()) continue
            cal.timeInMillis = src.lastModified()
            val year = cal.get(Calendar.YEAR)
            val month = "%02d".format(cal.get(Calendar.MONTH) + 1)
            moveFile(src, organizedDirFor(meta, "$year/$month", volumes), result, "Organize by date")
        }
    }

    private suspend fun organizeDownloadsByDate(files: List<FileMetadata>, result: ExecutionResult, volumes: List<StorageVolume>) {
        val cal = Calendar.getInstance()
        for (meta in files) {
            if (!FileTypeResolver.isDownload(meta)) continue
            val src = File(meta.absolutePath)
            if (!src.exists()) continue
            cal.timeInMillis = src.lastModified()
            val year = cal.get(Calendar.YEAR)
            val month = "%02d".format(cal.get(Calendar.MONTH) + 1)
            moveFile(src, organizedDirFor(meta, "Downloads/$year/$month", volumes), result, "Organize downloads by date")
        }
    }

    private suspend fun organizeMediaBySource(files: List<FileMetadata>, result: ExecutionResult, volumes: List<StorageVolume>) {
        for (meta in files) {
            val src = File(meta.absolutePath)
            if (!src.exists()) continue
            when {
                FileTypeResolver.isScreenshot(meta) -> moveFile(src, organizedDirFor(meta, "Media/Screenshots", volumes), result, "Media by source")
                FileTypeResolver.isWhatsAppImage(meta) || FileTypeResolver.isWhatsAppVideo(meta) ->
                    moveFile(src, organizedDirFor(meta, "Media/WhatsApp", volumes), result, "Media by source")
                FileTypeResolver.isTelegramMedia(meta) -> moveFile(src, organizedDirFor(meta, "Media/Telegram", volumes), result, "Media by source")
                FileTypeResolver.isCameraFile(meta) -> moveFile(src, organizedDirFor(meta, "Media/Camera", volumes), result, "Media by source")
            }
        }
    }

    /** Project roots are directories, not files — group scanned files by parent to find them. */
    private suspend fun organizeCodeProjects(files: List<FileMetadata>, result: ExecutionResult, volumes: List<StorageVolume>) {
        val filesByDir = files.groupBy { File(it.absolutePath).parentFile }
        val markerNames = setOf("build.gradle", "build.gradle.kts", "package.json", "pom.xml")
        val handledRoots = mutableSetOf<String>()

        for ((dir, dirFiles) in filesByDir) {
            currentCoroutineContext().ensureActive()
            if (dir == null || dir.absolutePath in handledRoots) continue
            val hasMarker = dirFiles.any { it.name in markerNames } || File(dir, ".git").exists()
            if (!hasMarker) continue
            val organized = organizedDirFor(dirFiles.first(), "Projects", volumes)
            val dest = resolveConflict(File(organized, dir.name))
            if (dir.renameTo(dest)) {
                result.add(ActionStatus.SUCCESS, dir.name, "Move project", "→ ${organized.path}")
                handledRoots += dir.absolutePath
            } else {
                result.add(ActionStatus.FAILED, dir.name, "Move project", "Could not move (cross-volume?)")
            }
        }
    }

    private suspend fun moveTelegramMedia(files: List<FileMetadata>, result: ExecutionResult, volumes: List<StorageVolume>) {
        for (meta in files) {
            if (!FileTypeResolver.isTelegramMedia(meta)) continue
            val sub = when {
                FileTypeResolver.isImage(meta.extension) -> "Telegram/Images"
                FileTypeResolver.isVideo(meta.extension) -> "Telegram/Videos"
                FileTypeResolver.isDocument(meta.extension) -> "Telegram/Documents"
                else -> "Telegram/Other"
            }
            moveFile(File(meta.absolutePath), organizedDirFor(meta, sub, volumes), result, "Move Telegram media")
        }
    }

    // ---- move to secondary storage ---------------------------------------------------------

    /** Picks a writable removable volume (SD card / USB-OTG) to move files onto. */
    private fun pickSdCard(): StorageVolume? =
        volumeManager.sdCardVolumes().firstOrNull { it.root.canWrite() }

    private suspend fun moveToSdCard(
        files: List<FileMetadata>, result: ExecutionResult, command: CommandType, subPath: String, action: String
    ) {
        val sdCard = pickSdCard()
        if (sdCard == null) {
            result.add(ActionStatus.FAILED, "-", action, "No SD card / secondary storage found")
            return
        }
        val dest = File(sdCard.root, subPath)
        for (meta in files) {
            if (CommandMatcher.matches(command, meta)) moveFile(File(meta.absolutePath), dest, result, action)
        }
    }

    // ---- cleanup --------------------------------------------------------------------------

    private suspend fun deleteDuplicates(files: List<FileMetadata>, result: ExecutionResult) {
        val hashToPath = HashMap<String, String>()
        // Group by size first — hashing every file is the expensive part, and two files with
        // different sizes can never be duplicates, so this skips most of the work up front.
        // Duplicates are matched across ALL volumes: a file on the SD card that's an exact copy
        // of one on internal storage is still a duplicate worth flagging.
        val bySize = files.groupBy { it.sizeBytes }
        for ((_, group) in bySize) {
            if (group.size < 2) continue
            for (meta in group) {
                currentCoroutineContext().ensureActive()
                val src = File(meta.absolutePath)
                if (!src.exists()) continue
                val hash = sha256(src) ?: continue
                val existing = hashToPath[hash]
                if (existing != null) {
                    if (src.delete()) result.add(ActionStatus.SUCCESS, meta.name, "Delete duplicate", "Duplicate of: $existing")
                    else result.add(ActionStatus.FAILED, meta.name, "Delete duplicate", "Could not delete")
                } else {
                    hashToPath[hash] = meta.absolutePath
                }
            }
        }
    }

    private suspend fun deleteEmptyFolders(result: ExecutionResult, volumes: List<StorageVolume>) {
        for (volume in volumes) {
            currentCoroutineContext().ensureActive()
            deleteEmptyFoldersRecursive(volume.root, result)
        }
    }

    private suspend fun deleteEmptyFoldersRecursive(dir: File, result: ExecutionResult): Boolean {
        currentCoroutineContext().ensureActive()
        if (!dir.exists() || !dir.isDirectory) return false
        val children = dir.listFiles() ?: return true
        var allGone = true
        for (child in children) {
            if (child.isDirectory) {
                val childEmpty = deleteEmptyFoldersRecursive(child, result)
                if (childEmpty && child.delete()) {
                    result.add(ActionStatus.SUCCESS, child.name, "Delete empty folder", child.parent ?: "")
                } else {
                    allGone = false
                }
            } else {
                allGone = false
            }
        }
        return allGone
    }

    private suspend fun deleteApks(files: List<FileMetadata>, result: ExecutionResult, unusedOnly: Boolean) {
        for (meta in files) {
            if (!FileTypeResolver.isApk(meta.extension)) continue
            currentCoroutineContext().ensureActive()
            val src = File(meta.absolutePath)
            if (!src.exists()) continue
            if (unusedOnly) {
                val pkg = getPackageFromApk(src)
                if (pkg == null || !isPackageInstalled(pkg)) {
                    if (src.delete()) result.add(ActionStatus.SUCCESS, meta.name, "Delete APK", "Not installed")
                    else result.add(ActionStatus.FAILED, meta.name, "Delete APK", "Could not delete")
                } else {
                    result.add(ActionStatus.SKIPPED, meta.name, "Delete APK", "Package still installed: $pkg")
                }
            } else {
                if (src.delete()) result.add(ActionStatus.SUCCESS, meta.name, "Delete APK", meta.parentPath)
                else result.add(ActionStatus.FAILED, meta.name, "Delete APK", "Could not delete")
            }
        }
    }

    private fun cleanAppCache(result: ExecutionResult, volumes: List<StorageVolume>, packageName: String, label: String) {
        var foundAny = false
        for (volume in volumes) {
            val cacheDir = File(volume.root, "Android/data/$packageName/cache")
            if (cacheDir.exists()) {
                foundAny = true
                deleteDirectoryContents(cacheDir, result, "Clean $label cache (${volume.label})")
            }
        }
        if (!foundAny) {
            result.add(ActionStatus.SKIPPED, label, "Clean cache", "Cache directory not found on any volume")
        }
    }

    private fun deleteDirectoryContents(dir: File, result: ExecutionResult, action: String) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isFile) {
                if (child.delete()) result.add(ActionStatus.SUCCESS, child.name, action, dir.path)
                else result.add(ActionStatus.FAILED, child.name, action, "Could not delete")
            } else if (child.isDirectory) {
                deleteDirectoryContents(child, result, action)
                child.delete()
            }
        }
    }

    private fun cleanGradleCache(result: ExecutionResult, volumes: List<StorageVolume>) {
        var foundAny = false
        for (volume in volumes) {
            val gradle = File(volume.root, ".gradle")
            if (gradle.exists()) {
                foundAny = true
                deleteDirectoryContents(gradle, result, "Clean Gradle cache (${volume.label})")
            }
        }
        if (!foundAny) {
            result.add(ActionStatus.SKIPPED, ".gradle", "Clean Gradle cache", "Not found on any volume")
        }
    }

    private suspend fun removeNodeModules(files: List<FileMetadata>, result: ExecutionResult) {
        val seenDirs = mutableSetOf<String>()
        for (meta in files) {
            val f = File(meta.absolutePath)
            val dir = f.parentFile ?: continue
            if (dir.name == "node_modules" && dir.absolutePath !in seenDirs) {
                currentCoroutineContext().ensureActive()
                seenDirs += dir.absolutePath
                deleteDirectoryContents(dir, result, "Remove node_modules")
                if (dir.delete()) result.add(ActionStatus.SUCCESS, "node_modules", "Remove node_modules", dir.parent ?: "")
            }
        }
    }

    private suspend fun cleanBuildOutputs(files: List<FileMetadata>, result: ExecutionResult) {
        val seenDirs = mutableSetOf<String>()
        val names = setOf("build", "dist", "out")
        for (meta in files) {
            val f = File(meta.absolutePath)
            val dir = f.parentFile ?: continue
            if (dir.name in names && dir.absolutePath !in seenDirs) {
                currentCoroutineContext().ensureActive()
                seenDirs += dir.absolutePath
                deleteDirectoryContents(dir, result, "Clean build output")
                dir.delete()
            }
        }
    }

    // ---- analysis -------------------------------------------------------------------------

    private fun findLargestFiles(files: List<FileMetadata>, result: ExecutionResult) {
        files.sortedByDescending { it.sizeBytes }.take(100).forEachIndexed { i, meta ->
            val location = if (meta.isRemovable) "SD Card" else "Internal"
            result.add(ActionStatus.SUCCESS, meta.name, "Largest file #${i + 1}", "${formatSize(meta.sizeBytes)} — [$location] ${meta.absolutePath}")
        }
    }

    private fun findUnusedFiles(files: List<FileMetadata>, result: ExecutionResult) {
        val cutoff = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000
        for (meta in files) {
            if (meta.lastModified < cutoff) {
                val location = if (meta.isRemovable) "SD Card" else "Internal"
                result.add(ActionStatus.SUCCESS, meta.name, "Unused file", "${formatSize(meta.sizeBytes)} — [$location] ${meta.parentPath}")
            }
        }
    }

    private fun analyzeStorage(files: List<FileMetadata>, result: ExecutionResult, volumes: List<StorageVolume>) {
        // metadata.json written before volume-tracking existed has a blank volumeRoot on every
        // entry, which wouldn't match any detected volume — fall back to one blended report
        // rather than silently showing nothing until the next rescan.
        if (files.isNotEmpty() && files.all { it.volumeRoot.isBlank() }) {
            analyzeStorageBlended(files, result)
            return
        }
        for (volume in volumes) {
            val onVolume = files.filter { it.volumeRoot == volume.root.absolutePath }
            if (onVolume.isEmpty()) continue
            val totalSize = onVolume.sumOf { it.sizeBytes }
            result.add(
                ActionStatus.SUCCESS, volume.label, "Volume overview",
                "${onVolume.size} files — ${formatSize(totalSize)} used, ${formatSize(volume.freeBytes)} free of ${formatSize(volume.totalBytes)}"
            )
            val stats = LinkedHashMap<String, LongArray>()
            for (meta in onVolume) {
                val cat = FileTypeResolver.resolveCategory(meta.extension)
                val entry = stats.getOrPut(cat) { longArrayOf(0, 0) }
                entry[0]++
                entry[1] += meta.sizeBytes
            }
            for ((cat, entry) in stats) {
                result.add(ActionStatus.SUCCESS, cat, "  ${volume.label} breakdown", "${entry[0]} files — ${formatSize(entry[1])}")
            }
        }
    }

    private fun analyzeStorageBlended(files: List<FileMetadata>, result: ExecutionResult) {
        result.add(
            ActionStatus.SKIPPED, "-", "Volume overview",
            "Per-volume breakdown unavailable for this data — rescan to enable it"
        )
        val stats = LinkedHashMap<String, LongArray>()
        for (meta in files) {
            val cat = FileTypeResolver.resolveCategory(meta.extension)
            val entry = stats.getOrPut(cat) { longArrayOf(0, 0) }
            entry[0]++
            entry[1] += meta.sizeBytes
        }
        for ((cat, entry) in stats) {
            result.add(ActionStatus.SUCCESS, cat, "Storage breakdown", "${entry[0]} files — ${formatSize(entry[1])}")
        }
    }

    // ---- misc -------------------------------------------------------------------------

    private fun sha256(file: File): String? = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(8192)
            var n: Int
            while (fis.read(buf).also { n = it } != -1) md.update(buf, 0, n)
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private fun getPackageFromApk(apk: File): String? = runCatching {
        context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.packageName
    }.getOrNull()

    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}
