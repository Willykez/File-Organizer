package com.willykez.files.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "file_organizer_prefs")

/** New in this rewrite — the original app persisted no user preferences at all. */
class PreferencesManager(private val context: Context) {

    private object Keys {
        val SELECTED_COMMANDS = stringSetPreferencesKey("selected_commands")
        val AUTO_ORGANIZE_ENABLED = booleanPreferencesKey("auto_organize_enabled")
        val NIGHTLY_CLEANUP_ENABLED = booleanPreferencesKey("nightly_cleanup_enabled")
    }

    val selectedCommandNames: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.SELECTED_COMMANDS] ?: emptySet() }

    suspend fun saveSelectedCommands(names: Set<String>) {
        context.dataStore.edit { it[Keys.SELECTED_COMMANDS] = names }
    }

    val autoOrganizeEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AUTO_ORGANIZE_ENABLED] ?: false }

    val nightlyCleanupEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.NIGHTLY_CLEANUP_ENABLED] ?: false }

    suspend fun setAutoOrganizeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_ORGANIZE_ENABLED] = enabled }
    }

    suspend fun setNightlyCleanupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NIGHTLY_CLEANUP_ENABLED] = enabled }
    }
}
