package com.willykez.files.domain

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

data class StorageVolume(
    val root: File,
    val label: String,
    val isRemovable: Boolean,
    val isPrimary: Boolean
) {
    val totalBytes: Long get() = runCatching { root.totalSpace }.getOrDefault(0L)
    val freeBytes: Long get() = runCatching { root.freeSpace }.getOrDefault(0L)
}

/**
 * Detects every mounted storage volume — internal storage plus any SD card or USB-OTG storage —
 * so commands can act across all of them instead of assuming everything lives on internal storage.
 *
 * The original app (and the first pass of this rewrite) only ever touched
 * `Environment.getExternalStorageDirectory()`, so cleanup commands like "delete empty folders" or
 * "clean Gradle cache" silently ignored an SD card even when one was present.
 */
class StorageVolumeManager(private val context: Context) {

    fun listVolumes(): List<StorageVolume> {
        val primaryRoot = Environment.getExternalStorageDirectory()
        val volumes = LinkedHashMap<String, StorageVolume>()

        if (primaryRoot != null && primaryRoot.exists()) {
            volumes[primaryRoot.absolutePath] = StorageVolume(
                root = primaryRoot,
                label = "Internal Storage",
                isRemovable = false,
                isPrimary = true
            )
        }

        // getExternalFilesDirs() returns one per-app directory per mounted volume (e.g.
        // ".../Android/data/com.willykez.files/files"); walking up 4 levels recovers each
        // volume's true root. This works across API levels without needing StorageManager's
        // newer (API 30+) getDirectory() call.
        context.getExternalFilesDirs(null)?.forEachIndexed { index, dir ->
            if (dir == null) return@forEachIndexed
            var root: File = dir
            repeat(4) { root.parentFile?.let { root = it } }
            if (volumes.containsKey(root.absolutePath) || !root.exists()) return@forEachIndexed

            val removable = isRemovableVolume(root)
            volumes[root.absolutePath] = StorageVolume(
                root = root,
                label = friendlyLabel(root, index, removable),
                isRemovable = removable,
                isPrimary = false
            )
        }

        return volumes.values.toList()
    }

    fun sdCardVolumes(): List<StorageVolume> = listVolumes().filter { it.isRemovable }

    fun primaryVolume(): StorageVolume? = listVolumes().firstOrNull { it.isPrimary }

    /** Wraps an arbitrary folder as a pseudo-volume so folder-scoped commands can reuse all the
     *  same per-volume executor logic (organize destination placement, empty-folder walk, etc.)
     *  without duplicating it. [isRemovable] is inherited from whichever real volume the folder
     *  is physically under. */
    fun asVolume(folder: java.io.File): StorageVolume {
        val real = volumeContaining(folder.absolutePath)
        return StorageVolume(
            root = folder,
            label = folder.name.ifBlank { folder.absolutePath },
            isRemovable = real?.isRemovable ?: false,
            isPrimary = false
        )
    }

    /** The volume whose root is the longest matching prefix of [absolutePath]. */
    fun volumeContaining(absolutePath: String, knownVolumes: List<StorageVolume> = listVolumes()): StorageVolume? =
        knownVolumes.filter { absolutePath.startsWith(it.root.absolutePath) }
            .maxByOrNull { it.root.absolutePath.length }

    private fun isRemovableVolume(root: File): Boolean = runCatching {
        Environment.isExternalStorageRemovable(root)
    }.getOrDefault(false)

    private fun friendlyLabel(root: File, index: Int, removable: Boolean): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                val volume = storageManager?.getStorageVolume(root)
                volume?.getDescription(context)?.let { return it }
            }
        }
        return if (removable) "SD Card${if (index > 1) " $index" else ""}" else "External Storage $index"
    }
}
