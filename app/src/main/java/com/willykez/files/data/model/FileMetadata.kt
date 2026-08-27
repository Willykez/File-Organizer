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
    val lastModified: Long
)
