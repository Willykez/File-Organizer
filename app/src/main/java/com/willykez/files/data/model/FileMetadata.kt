package com.willykez.files.data.model

import kotlinx.serialization.Serializable

/**
 * A single indexed file discovered by the storage scanner.
 *
 * Kept intentionally small — this is what gets persisted to metadata.json and
 * re-loaded on every app launch, so extra fields have a real storage/parse cost
 * across devices with hundreds of thousands of files.
 */
@Serializable
data class FileMetadata(
    val absolutePath: String,
    val name: String,
    val parentPath: String,
    val sizeBytes: Long,
    val extension: String,
    val lastModified: Long,
    /**
     * Absolute path of the storage volume root this file lives under (e.g. "/storage/emulated/0"
     * for internal, "/storage/1A2B-3C4D" for an SD card). Defaults to "" so metadata.json files
     * saved before this field existed still deserialize instead of failing to load.
     */
    val volumeRoot: String = "",
    /** True if [volumeRoot] is a removable volume (SD card / USB-OTG), false for internal storage. */
    val isRemovable: Boolean = false
)

