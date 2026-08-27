package com.willykez.files.data

import com.willykez.files.data.model.FileMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTypeResolverTest {

    private fun meta(name: String, parent: String, ext: String, size: Long = 1024) = FileMetadata(
        absolutePath = "$parent/$name", name = name, parentPath = parent,
        sizeBytes = size, extension = ext, lastModified = System.currentTimeMillis()
    )

    @Test
    fun `resolves common categories`() {
        assertEquals(FileTypeResolver.IMAGE, FileTypeResolver.resolveCategory("jpg"))
        assertEquals(FileTypeResolver.VIDEO, FileTypeResolver.resolveCategory("mp4"))
        assertEquals(FileTypeResolver.DOCUMENT, FileTypeResolver.resolveCategory("pdf"))
        assertEquals(FileTypeResolver.APK, FileTypeResolver.resolveCategory("apk"))
        assertEquals(FileTypeResolver.OTHER, FileTypeResolver.resolveCategory(""))
    }

    @Test
    fun `detects screenshots by name or folder`() {
        val byName = meta("Screenshot_2026.png", "/storage/emulated/0/Pictures", "png")
        val byFolder = meta("img001.png", "/storage/emulated/0/Pictures/Screenshots", "png")
        assertTrue(FileTypeResolver.isScreenshot(byName))
        assertTrue(FileTypeResolver.isScreenshot(byFolder))
    }

    @Test
    fun `detects whatsapp images only under whatsapp images folder`() {
        val wa = meta("IMG-001.jpg", "/storage/emulated/0/WhatsApp/Media/WhatsApp Images", "jpg")
        val notWa = meta("IMG-001.jpg", "/storage/emulated/0/DCIM/Camera", "jpg")
        assertTrue(FileTypeResolver.isWhatsAppImage(wa))
        assertTrue(!FileTypeResolver.isWhatsAppImage(notWa))
    }
}
