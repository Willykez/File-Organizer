package com.willykez.files.domain

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
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

class CommandExecutor(private val context: Context) {

    suspend fun execute(command: CommandType, metadata: List<FileMetadata>): ExecutionResult =
        withContext(Dispatchers.IO) {
            val result = ExecutionResult(command)
            when (command) {
                CommandType.ORGANIZE_BY_TYPE -> organizeByType(metadata, result)
                CommandType.ORGANIZE_BY_DATE -> organizeByDate(metadata, result)
                CommandType.ORGANIZE_DOWNLOADS_BY_DATE -> organizeDownloadsByDate(metadata, result)
                CommandType.ORGANIZE_MEDIA_BY_SOURCE -> organizeMediaBySource(metadata, result)
                CommandType.ORGANIZE_CODE_PROJECTS -> organizeCodeProjects(metadata, result)
                CommandType.ORGANIZE_SCREEN_RECORDINGS ->
                    moveMatching(metadata, result, command, organizedDir("Videos/ScreenRecordings"), "Move screen recording")

                CommandType.MOVE_SCREENSHOTS -> moveMatching(metadata, result, command, organizedDir("Screenshots"), "Move screenshot")
                CommandType.MOVE_WHATSAPP_IMAGES -> moveMatching(metadata, result, command, organizedDir("WhatsApp/Images"), "Move WhatsApp image")
                CommandType.MOVE_WHATSAPP_VIDEOS -> moveMatching(metadata, result, command, organizedDir("WhatsApp/Videos"), "Move WhatsApp video")
                CommandType.MOVE_TELEGRAM_MEDIA -> moveTelegramMedia(metadata, result)
                CommandType.MOVE_DOWNLOADS -> moveMatching(metadata, result, command, organizedDir("Downloads"), "Move download")
                CommandType.MOVE_LARGE_FILES -> moveMatching(metadata, result, command, organizedDir("LargeFiles"), "Move large file")
                CommandType.MOVE_OLD_FILES -> moveMatching(metadata, result, command, organizedDir("OldFiles"), "Move old file")
                CommandType.MOVE_AUDIO -> moveMatching(metadata, result, command, organizedDir(FileTypeResolver.AUDIO), "Move audio")
                CommandType.MOVE_VIDEOS -> moveMatching(metadata, result, command, organizedDir(FileTypeResolver.VIDEO), "Move video")
                CommandType.MOVE_DOCUMENTS -> moveMatching(metadata, result, command, organizedDir(FileTypeResolver.DOCUMENT), "Move document")
                CommandType.MOVE_IMAGES -> moveMatching(metadata, result, command, organizedDir(FileTypeResolver.IMAGE), "Move image")
                CommandType.MOVE_MEDIA_TO_SDCARD -> moveToSdCard(metadata, result, command, "Organized/Media", "Move media to SD")
                CommandType.MOVE_CAMERA_TO_SDCARD -> moveToSdCard(metadata, result, command, "DCIM/Camera", "Move camera file to SD")
                CommandType.OFFLOAD_LARGE_VIDEOS -> moveToSdCard(metadata, result, command, "Organized/LargeVideos", "Offload large video")
                CommandType.MOVE_ARCHIVES_TO_EXTERNAL -> moveToSdCard(metadata, result, command, "Organized/Archives", "Move archive to SD")

                CommandType.DELETE_DUPLICATES -> deleteDuplicates(metadata, result)
                CommandType.DELETE_EMPTY_FOLDERS -> deleteEmptyFolders(result)
                CommandType.DELETE_TEMP_FILES -> deleteMatching(metadata, result, command, "Delete temp file")
                CommandType.DELETE_APKS -> deleteApks(metadata, result, unusedOnly = false)
                CommandType.DELETE_UNUSED_APKS -> deleteApks(metadata, result, unusedOnly = true)
                CommandType.DELETE_THUMBNAILS -> deleteMatching(metadata, result, command, "Delete thumbnail")
                CommandType.DELETE_CRASH_LOGS -> deleteMatching(metadata, result, command, "Delete crash log")

                CommandType.CLEAN_WHATSAPP_SENT_JUNK -> deleteMatching(metadata, result, command, "Clean WhatsApp sent")
                CommandType.CLEAN_TIKTOK_CACHE -> cleanAppCache(result, "com.zhiliaoapp.musically", "TikTok")
                CommandType.CLEAN_INSTAGRAM_CACHE -> cleanAppCache(result, "com.instagram.android", "Instagram")
                CommandType.CLEAN_GRADLE_CACHE -> cleanGradleCache(result)
                CommandType.REMOVE_NODE_MODULES -> removeNodeModules(metadata, result)
                CommandType.CLEAN_BUILD_OUTPUTS -> cleanBuildOutputs(metadata, result)

                CommandType.FIND_LARGEST_FILES -> findLargestFiles(metadata, result)
                CommandType.FIND_UNUSED_FILES -> findUnusedFiles(metadata, result)
                CommandType.ANALYZE_STORAGE_USAGE -> analyzeStorage(metadata, result)

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

    private fun organizedDir(sub: String): File =
        File(Environment.getExternalStorageDirectory(), "Organized/$sub").apply { mkdirs() }

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

    private suspend fun moveMatching(
        metadata: List<FileMetadata>, result: ExecutionResult, command: CommandType, destDir: File, action: String
    ) {
        for (meta in metadata) {
            if (CommandMatcher.matches(command, meta)) moveFile(File(meta.absolutePath), destDir, result, action)
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

    private suspend fun organizeByType(files: List<FileMetadata>, result: ExecutionResult) {
        for (meta in files) {
            val cat = FileTypeResolver.resolveCategory(meta.extension)
            moveFile(File(meta.absolutePath), organizedDir(cat), result, "Organize by type")
        }
    }

    private suspend fun organizeByDate(files: List<FileMetadata>, result: ExecutionResult) {
        val cal = Calendar.getInstance()
        for (meta in files) {
            val src = File(meta.absolutePath)
            if (!src.exists()) continue
            cal.timeInMillis = src.lastModified()
            val year = cal.get(Calendar.YEAR)
            val month = "%02d".format(cal.get(Calendar.MONTH) + 1)
            moveFile(src, organizedDir("$year/$month"), result, "Organize by date")
        }
    }

    private suspend fun organizeDownloadsByDate(files: List<FileMetadata>, result: ExecutionResult) {
        val cal = Calendar.getInstance()
        for (meta in files) {
            if (!FileTypeResolver.isDownload(meta)) continue
            val src = File(meta.absolutePath)
            if (!src.exists()) continue
            cal.timeInMillis = src.lastModified()
            val year = cal.get(Calendar.YEAR)
            val month = "%02d".format(cal.get(Calendar.MONTH) + 1)
            moveFile(src, organizedDir("Downloads/$year/$month"), result, "Organize downloads by date")
        }
    }

    private suspend fun organizeMediaBySource(files: List<FileMetadata>, result: ExecutionResult) {
        for (meta in files) {
            val src = File(meta.absolutePath)
            if (!src.exists()) continue
            when {
                FileTypeResolver.isScreenshot(meta) -> moveFile(src, organizedDir("Media/Screenshots"), result, "Media by source")
                FileTypeResolver.isWhatsAppImage(meta) || FileTypeResolver.isWhatsAppVideo(meta) ->
                    moveFile(src, organizedDir("Media/WhatsApp"), result, "Media by source")
                FileTypeResolver.isTelegramMedia(meta) -> moveFile(src, organizedDir("Media/Telegram"), result, "Media by source")
                FileTypeResolver.isCameraFile(meta) -> moveFile(src, organizedDir("Media/Camera"), result, "Media by source")
            }
        }
    }

    /** Project roots are directories, not files — group scanned files by parent to find them. */
    private suspend fun organizeCodeProjects(files: List<FileMetadata>, result: ExecutionResult) {
        val organized = organizedDir("Projects")
        val filesByDir = files.groupBy { File(it.absolutePath).parentFile }
        val markerNames = setOf("build.gradle", "build.gradle.kts", "package.json", "pom.xml")
        val handledRoots = mutableSetOf<String>()

        for ((dir, dirFiles) in filesByDir) {
            currentCoroutineContext().ensureActive()
            if (dir == null || dir.absolutePath in handledRoots) continue
            val hasMarker = dirFiles.any { it.name in markerNames } || File(dir, ".git").exists()
            if (!hasMarker) continue
            val dest = resolveConflict(File(organized, dir.name))
            if (dir.renameTo(dest)) {
                result.add(ActionStatus.SUCCESS, dir.name, "Move project", "→ ${organized.path}")
                handledRoots += dir.absolutePath
            } else {
                result.add(ActionStatus.FAILED, dir.name, "Move project", "Could not move (cross-volume?)")
            }
        }
    }

    private suspend fun moveTelegramMedia(files: List<FileMetadata>, result: ExecutionResult) {
        for (meta in files) {
            if (!FileTypeResolver.isTelegramMedia(meta)) continue
            val sub = when {
                FileTypeResolver.isImage(meta.extension) -> "Telegram/Images"
                FileTypeResolver.isVideo(meta.extension) -> "Telegram/Videos"
                FileTypeResolver.isDocument(meta.extension) -> "Telegram/Documents"
                else -> "Telegram/Other"
            }
            moveFile(File(meta.absolutePath), organizedDir(sub), result, "Move Telegram media")
        }
    }

    // ---- move to secondary storage ---------------------------------------------------------

    private fun getSdCardRoot(): File? {
        val primary = Environment.getExternalStorageDirectory()
        val dirs = context.getExternalFilesDirs(null) ?: return null
        for (dir in dirs) {
            if (dir == null) continue
            var root: File = dir
            repeat(4) { root.parentFile?.let { root = it } }
            if (root != primary && root.exists() && root.canWrite()) return root
        }
        return null
    }

    private suspend fun moveToSdCard(
        files: List<FileMetadata>, result: ExecutionResult, command: CommandType, subPath: String, action: String
    ) {
        val sdCard = getSdCardRoot()
        if (sdCard == null) {
            result.add(ActionStatus.FAILED, "-", action, "No SD card / secondary storage found")
            return
        }
        val dest = File(sdCard, subPath)
        for (meta in files) {
            if (CommandMatcher.matches(command, meta)) moveFile(File(meta.absolutePath), dest, result, action)
        }
    }

    // ---- cleanup --------------------------------------------------------------------------

    private suspend fun deleteDuplicates(files: List<FileMetadata>, result: ExecutionResult) {
        val hashToPath = HashMap<String, String>()
        // Group by size first — hashing every file is the expensive part, and two files with
        // different sizes can never be duplicates, so this skips most of the work up front.
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

    private suspend fun deleteEmptyFolders(result: ExecutionResult) {
        deleteEmptyFoldersRecursive(Environment.getExternalStorageDirectory(), result)
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
                } else if (!childEmpty) {
                    allGone = false
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

    private fun cleanAppCache(result: ExecutionResult, packageName: String, label: String) {
        val cacheDir = File(Environment.getExternalStorageDirectory(), "Android/data/$packageName/cache")
        if (!cacheDir.exists()) {
            result.add(ActionStatus.SKIPPED, label, "Clean cache", "Cache directory not found")
            return
        }
        deleteDirectoryContents(cacheDir, result, "Clean $label cache")
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

    private fun cleanGradleCache(result: ExecutionResult) {
        val gradle = File(Environment.getExternalStorageDirectory(), ".gradle")
        if (!gradle.exists()) {
            result.add(ActionStatus.SKIPPED, ".gradle", "Clean Gradle cache", "Not found")
            return
        }
        deleteDirectoryContents(gradle, result, "Clean Gradle cache")
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
            result.add(ActionStatus.SUCCESS, meta.name, "Largest file #${i + 1}", "${formatSize(meta.sizeBytes)} — ${meta.absolutePath}")
        }
    }

    private fun findUnusedFiles(files: List<FileMetadata>, result: ExecutionResult) {
        val cutoff = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000
        for (meta in files) {
            if (meta.lastModified < cutoff) {
                result.add(ActionStatus.SUCCESS, meta.name, "Unused file", "${formatSize(meta.sizeBytes)} — ${meta.parentPath}")
            }
        }
    }

    private fun analyzeStorage(files: List<FileMetadata>, result: ExecutionResult) {
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
