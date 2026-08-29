package com.willykez.files.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
        val STORAGE_SCOPE = stringPreferencesKey("storage_scope")
        val PROTECTED_FOLDERS = stringSetPreferencesKey("protected_folders")
        val SKIP_HIDDEN_FOLDERS = booleanPreferencesKey("skip_hidden_folders")
        val AUTO_RESCAN_AFTER_COMMANDS = booleanPreferencesKey("auto_rescan_after_commands")
        val CONFIRM_BEFORE_RUN = booleanPreferencesKey("confirm_before_run")
        val AUTO_PROTECT_ENABLED = booleanPreferencesKey("auto_protect_enabled")
        val AUTOMATION_NOTIFICATIONS = booleanPreferencesKey("automation_notifications")
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

    /** Which storage volume(s) commands should act on — stores a [com.willykez.files.ui.StorageScope] name. */
    val storageScope: Flow<String> =
        context.dataStore.data.map { it[Keys.STORAGE_SCOPE] ?: "ALL" }

    suspend fun setStorageScope(scope: String) {
        context.dataStore.edit { it[Keys.STORAGE_SCOPE] = scope }
    }

    /** Absolute folder paths bulk commands must never reach into — user-marked, on top of the
     *  auto-detected project/firmware roots from [com.willykez.files.domain.ProtectionRules]. */
    val protectedFolders: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.PROTECTED_FOLDERS] ?: emptySet() }

    suspend fun addProtectedFolder(path: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.PROTECTED_FOLDERS] ?: emptySet()
            prefs[Keys.PROTECTED_FOLDERS] = current + path
        }
    }

    suspend fun removeProtectedFolder(path: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.PROTECTED_FOLDERS] ?: emptySet()
            prefs[Keys.PROTECTED_FOLDERS] = current - path
        }
    }

    // ---- scanning & behavior settings ----------------------------------------------------

    val skipHiddenFolders: Flow<Boolean> = context.dataStore.data.map { it[Keys.SKIP_HIDDEN_FOLDERS] ?: true }
    suspend fun setSkipHiddenFolders(v: Boolean) { context.dataStore.edit { it[Keys.SKIP_HIDDEN_FOLDERS] = v } }

    val autoRescanAfterCommands: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_RESCAN_AFTER_COMMANDS] ?: false }
    suspend fun setAutoRescanAfterCommands(v: Boolean) { context.dataStore.edit { it[Keys.AUTO_RESCAN_AFTER_COMMANDS] = v } }

    val confirmBeforeRun: Flow<Boolean> = context.dataStore.data.map { it[Keys.CONFIRM_BEFORE_RUN] ?: true }
    suspend fun setConfirmBeforeRun(v: Boolean) { context.dataStore.edit { it[Keys.CONFIRM_BEFORE_RUN] = v } }

    val autoProtectEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_PROTECT_ENABLED] ?: true }
    suspend fun setAutoProtectEnabled(v: Boolean) { context.dataStore.edit { it[Keys.AUTO_PROTECT_ENABLED] = v } }

    val automationNotifications: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTOMATION_NOTIFICATIONS] ?: true }
    suspend fun setAutomationNotifications(v: Boolean) { context.dataStore.edit { it[Keys.AUTOMATION_NOTIFICATIONS] = v } }
}
