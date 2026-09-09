package com.willykez.files.data.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationRuleTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serializes and restores a folder-scoped rule`() {
        val rule = AutomationRule(
            id = "abc-123",
            name = "Clean my Downloads",
            commandNames = listOf("DELETE_TEMP_FILES", "MOVE_LARGE_FILES"),
            folderPath = "/storage/emulated/0/Download",
            folderLabel = "Download",
            storageScope = "ALL",
            intervalHours = 12,
            enabled = true
        )
        val decoded = json.decodeFromString<AutomationRule>(json.encodeToString(rule))
        assertEquals(rule, decoded)
    }

    @Test
    fun `serializes and restores a storage-scope rule with no folder`() {
        val rule = AutomationRule(
            id = "def-456",
            name = "Weekly SD card tidy",
            commandNames = listOf("DELETE_EMPTY_FOLDERS"),
            folderPath = null,
            folderLabel = null,
            storageScope = "SD_CARD",
            intervalHours = 168,
            enabled = false
        )
        val decoded = json.decodeFromString<AutomationRule>(json.encodeToString(rule))
        assertEquals(rule, decoded)
        assertEquals(null, decoded.folderPath)
    }
}
