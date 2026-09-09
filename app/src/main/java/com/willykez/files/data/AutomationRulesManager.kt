package com.willykez.files.data

import android.content.Context
import com.willykez.files.data.model.AutomationRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Stores custom automation rules in the app's private storage (`filesDir`) — these are app
 * configuration, not user documents, so unlike `metadata.json` (kept in public Documents for
 * visibility) they don't need to be user-browsable.
 */
class AutomationRulesManager(private val context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun rulesFile(): File = File(context.filesDir, "automation_rules.json")

    suspend fun loadRules(): List<AutomationRule> = withContext(Dispatchers.IO) {
        val file = rulesFile()
        if (!file.exists()) return@withContext emptyList()
        runCatching { json.decodeFromString<List<AutomationRule>>(file.readText()) }.getOrDefault(emptyList())
    }

    suspend fun saveRules(rules: List<AutomationRule>) = withContext(Dispatchers.IO) {
        runCatching { rulesFile().writeText(json.encodeToString(rules)) }
    }

    suspend fun upsertRule(rule: AutomationRule): List<AutomationRule> = withContext(Dispatchers.IO) {
        val current = loadRules().toMutableList()
        val index = current.indexOfFirst { it.id == rule.id }
        if (index >= 0) current[index] = rule else current += rule
        saveRules(current)
        current
    }

    suspend fun removeRule(id: String): List<AutomationRule> = withContext(Dispatchers.IO) {
        val current = loadRules().filterNot { it.id == id }
        saveRules(current)
        current
    }

    suspend fun getRule(id: String): AutomationRule? = loadRules().firstOrNull { it.id == id }
}
