package com.willykez.files.domain

import android.content.Context
import android.os.Environment
import com.willykez.files.data.model.FileMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque

data class ScanProgress(val filesFound: Int, val currentFolder: String)

/**
 * Walks external storage (and any secondary volumes) for file metadata.
 *
 * Rewritten from the original recursive Java implementation to an iterative, coroutine-friendly
 * walk: a deep folder tree (node_modules, .git, gradle caches…) could previously blow the call
 * stack with recursion; this uses an explicit work queue instead, and checks for cancellation so
 * the user can back out of a long scan without killing the app.
 */
class StorageScanner(private val context: Context) {

    private val excludedFolders = setOf("android", "lost.dir")

    suspend fun scanAll(onProgress: suspend (ScanProgress) -> Unit = {}): List<FileMetadata> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<FileMetadata>()
            val roots = mutableListOf<File>()

            Environment.getExternalStorageDirectory()?.let { if (it.exists()) roots += it }

            val primary = Environment.getExternalStorageDirectory()
            context.getExternalFilesDirs(null)?.forEach { dir ->
                if (dir == null) return@forEach
                var root: File = dir
                repeat(4) { root.parentFile?.let { root = it } }
                if (root != primary && root.exists()) roots += root
            }

            val queue = ArrayDeque<File>()
            roots.forEach { queue.add(it) }

            while (queue.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val dir = queue.poll() ?: continue
                if (!dir.canRead()) continue
                val children = dir.listFiles() ?: continue

                for (child in children) {
                    if (child.isDirectory) {
                        val n = child.name.lowercase()
                        if (n in excludedFolders || n.startsWith(".")) continue
                        queue.add(child)
                    } else {
                        result += child.toMetadata()
                    }
                }
                if (result.size % 250 < children.size) {
                    onProgress(ScanProgress(result.size, dir.path))
                }
            }
            result
        }

    private fun File.toMetadata(): FileMetadata {
        val dot = name.lastIndexOf('.')
        val ext = if (dot > 0 && dot != name.length - 1) name.substring(dot + 1).lowercase() else ""
        return FileMetadata(
            absolutePath = absolutePath,
            name = name,
            parentPath = parent ?: "",
            sizeBytes = length(),
            extension = ext,
            lastModified = lastModified()
        )
    }
}
