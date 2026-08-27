package com.willykez.files.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileMetadataTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serializes and restores volume fields`() {
        val meta = FileMetadata(
            absolutePath = "/storage/1A2B-3C4D/DCIM/photo.jpg",
            name = "photo.jpg",
            parentPath = "/storage/1A2B-3C4D/DCIM",
            sizeBytes = 2048,
            extension = "jpg",
            lastModified = 1000L,
            volumeRoot = "/storage/1A2B-3C4D",
            isRemovable = true
        )
        val encoded = json.encodeToString(meta)
        val decoded = json.decodeFromString<FileMetadata>(encoded)
        assertEquals(meta, decoded)
        assertTrue(decoded.isRemovable)
    }

    @Test
    fun `old metadata json without volume fields still deserializes`() {
        // Simulates a metadata.json written before volume tracking existed.
        val legacyJson = """
            {"absolutePath":"/storage/emulated/0/a.txt","name":"a.txt","parentPath":"/storage/emulated/0",
             "sizeBytes":10,"extension":"txt","lastModified":5}
        """.trimIndent()
        val decoded = json.decodeFromString<FileMetadata>(legacyJson)
        assertEquals("", decoded.volumeRoot)
        assertFalse(decoded.isRemovable)
    }
}
