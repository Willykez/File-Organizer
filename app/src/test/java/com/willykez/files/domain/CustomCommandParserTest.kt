package com.willykez.files.domain

import com.willykez.files.data.model.CustomActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CustomCommandParserTest {

    private val internal = StorageVolume(File("/storage/emulated/0"), "Internal Storage", isRemovable = false, isPrimary = true)
    private val sdCard = StorageVolume(File("/storage/1A2B-3C4D"), "SD Card", isRemovable = true, isPrimary = false)
    private val volumes = listOf(internal, sdCard)
    private val availableFolders = setOf(
        "/storage/emulated/0/Download",
        "/storage/emulated/0/DCIM/Camera",
        "/storage/emulated/0/Pictures"
    )

    @Test
    fun `parses the motivating example end to end`() {
        val action = CustomCommandParser.parseOffline(
            "Go to the Internal storage Download folder fetch for .mkv files and move alls to SD Card Movie folder",
            volumes, availableFolders
        )
        assertNotNull(action)
        action!!
        assertEquals(CustomActionType.MOVE, action.actionType)
        assertEquals(setOf("mkv"), action.extensions)
        assertEquals("/storage/emulated/0/Download", action.sourceFolderPath)
        assertEquals("/storage/1A2B-3C4D/Movie", action.destinationPath)
    }

    @Test
    fun `does not fabricate a source folder that was never scanned`() {
        // "Screenshots" was never scanned, so the source filter must stay unset rather than
        // silently pointing at a made-up path — the action still proceeds (extensions +
        // destination alone are enough to act on), just without a bogus source restriction.
        val action = CustomCommandParser.parseOffline(
            "move all png files from Screenshots to sd card backup folder",
            volumes, availableFolders
        )
        assertNotNull(action)
        assertNull(action!!.sourceFolderPath)
        assertEquals(setOf("png"), action.extensions)
    }

    @Test
    fun `delete request needs no destination`() {
        val action = CustomCommandParser.parseOffline(
            "delete all mkv files from the download folder",
            volumes, availableFolders
        )
        assertNotNull(action)
        action!!
        assertEquals(CustomActionType.DELETE, action.actionType)
        assertNull(action.destinationPath)
    }

    @Test
    fun `requesting the sd card when none is present fails rather than silently using internal storage`() {
        val action = CustomCommandParser.parseOffline(
            "move all mkv files from download to sd card movie folder",
            listOf(internal), // no SD card in this list
            availableFolders
        )
        assertNull(action)
    }

    @Test
    fun `plain conversation is not treated as a command`() {
        val action = CustomCommandParser.parseOffline("how much free space do I have", volumes, availableFolders)
        assertNull(action)
    }

    @Test
    fun `matchFiles never matches everything when no filter resolved`() {
        val bogusAction = com.willykez.files.data.model.CustomAction(
            summary = "x", actionType = CustomActionType.DELETE,
            sourceFolderPath = null, sourceFolderLabel = null,
            extensions = emptySet(), nameContains = null,
            destinationPath = null, destinationLabel = null
        )
        val meta = listOf(
            com.willykez.files.data.model.FileMetadata("/a/b.txt", "b.txt", "/a", 1, "txt", 0)
        )
        assertTrue(CustomCommandParser.matchFiles(bogusAction, meta).isEmpty())
    }
}
