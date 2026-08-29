package com.willykez.files.domain

import com.willykez.files.data.model.FileMetadata
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionRulesTest {

    private fun meta(path: String, size: Long = 1024) = FileMetadata(
        absolutePath = path,
        name = path.substringAfterLast('/'),
        parentPath = path.substringBeforeLast('/'),
        sizeBytes = size,
        extension = path.substringAfterLast('.', missingDelimiterValue = ""),
        lastModified = 0L
    )

    @Test
    fun `detects an extracted android project by build_gradle marker`() {
        val files = listOf(
            meta("/storage/emulated/0/MyApp/build.gradle"),
            meta("/storage/emulated/0/MyApp/settings.gradle"),
            meta("/storage/emulated/0/MyApp/app/src/main/java/com/example/MainActivity.kt"),
            meta("/storage/emulated/0/MyApp/app/src/main/res/values/strings.xml"),
            meta("/storage/emulated/0/Pictures/vacation.jpg")
        )
        val roots = ProtectionRules.detectProtectedRoots(files)
        assertTrue(roots.contains("/storage/emulated/0/MyApp"))

        // Every file under the project root is protected, regardless of nesting depth.
        assertTrue(ProtectionRules.isProtected("/storage/emulated/0/MyApp/app/src/main/java/com/example/MainActivity.kt", roots))
        assertTrue(ProtectionRules.isProtected("/storage/emulated/0/MyApp/app/src/main/res/values/strings.xml", roots))

        // An unrelated file elsewhere on the device is not.
        assertFalse(ProtectionRules.isProtected("/storage/emulated/0/Pictures/vacation.jpg", roots))
    }

    @Test
    fun `detects a firmware dump by sibling system vendor folders`() {
        val files = listOf(
            meta("/storage/emulated/0/stock_rom/system/build.prop"),
            meta("/storage/emulated/0/stock_rom/vendor/etc/init.rc"),
            meta("/storage/emulated/0/stock_rom/META-INF/com/google/android/update-binary"),
            meta("/storage/emulated/0/Download/other_file.zip")
        )
        val roots = ProtectionRules.detectProtectedRoots(files)
        assertTrue(roots.contains("/storage/emulated/0/stock_rom"))
        assertTrue(ProtectionRules.isProtected("/storage/emulated/0/stock_rom/system/build.prop", roots))
        assertFalse(ProtectionRules.isProtected("/storage/emulated/0/Download/other_file.zip", roots))
    }

    @Test
    fun `does not protect ordinary unrelated folders`() {
        val files = listOf(
            meta("/storage/emulated/0/DCIM/Camera/IMG_0001.jpg"),
            meta("/storage/emulated/0/Download/report.pdf")
        )
        val roots = ProtectionRules.detectProtectedRoots(files)
        assertTrue(roots.isEmpty())
    }

    @Test
    fun `explicit folder scope into a protected root counts as override`() {
        val roots = setOf("/storage/emulated/0/MyApp")
        assertTrue(ProtectionRules.isExplicitlyScopedInto("/storage/emulated/0/MyApp", roots))
        assertTrue(ProtectionRules.isExplicitlyScopedInto("/storage/emulated/0/MyApp/app", roots))
        assertFalse(ProtectionRules.isExplicitlyScopedInto("/storage/emulated/0/Download", roots))
    }
}
