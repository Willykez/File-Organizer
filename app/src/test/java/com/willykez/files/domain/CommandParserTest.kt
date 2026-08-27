package com.willykez.files.domain

import com.willykez.files.data.model.CommandType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {

    @Test
    fun `matches english keywords`() {
        assertEquals(CommandType.MOVE_SCREENSHOTS, CommandParser.matchOffline("move my screenshots please"))
        assertEquals(CommandType.DELETE_DUPLICATES, CommandParser.matchOffline("find duplicates on my phone"))
        assertEquals(CommandType.CLEAN_GRADLE_CACHE, CommandParser.matchOffline("clean gradle cache"))
    }

    @Test
    fun `matches swahili keywords`() {
        assertEquals(CommandType.MOVE_SCREENSHOTS, CommandParser.matchOffline("hamisha picha za skrini"))
        assertEquals(CommandType.DELETE_TEMP_FILES, CommandParser.matchOffline("futa faili za muda"))
    }

    @Test
    fun `unknown input returns UNKNOWN`() {
        assertEquals(CommandType.UNKNOWN, CommandParser.matchOffline("what is the weather today"))
    }

    @Test
    fun `looksLikeCommand detects action verbs`() {
        assertTrue(CommandParser.looksLikeCommand("delete my temp files"))
        assertTrue(CommandParser.looksLikeCommand("panga picha zangu"))
    }
}
