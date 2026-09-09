package com.willykez.files.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.willykez.files.data.AutomationRulesManager
import com.willykez.files.data.PreferencesManager
import com.willykez.files.data.model.CommandType
import com.willykez.files.data.model.ExecutionResult
import com.willykez.files.data.model.FileMetadata
import com.willykez.files.domain.CommandExecutor
import com.willykez.files.domain.ProtectionRules
import com.willykez.files.domain.StorageVolume
import com.willykez.files.domain.StorageVolumeManager
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Executes one user-defined [com.willykez.files.data.model.AutomationRule] — scoped to a folder
 * the user picked (or a coarser internal/SD-card scope), running whichever commands the rule
 * specifies.
 *
 * Protection is always enforced in full here, with no override — the folder-scope override that
 * lets a manual run proceed inside a protected folder exists because a person is present in that
 * moment to make that call; a rule set up once and then run unattended, possibly weeks later,
 * doesn't get that same informed-consent exception even if it happens to be scoped onto a folder
 * that's since become a detected project/firmware root.
 */
class AutomationRuleWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ruleId = inputData.getString(KEY_RULE_ID) ?: return Result.failure()
        val rulesManager = AutomationRulesManager(applicationContext)
        val rule = rulesManager.getRule(ruleId) ?: return Result.success() // rule was deleted since scheduling
        if (!rule.enabled) return Result.success()

        val commands = rule.commandNames.mapNotNull { runCatching { CommandType.valueOf(it) }.getOrNull() }
        if (commands.isEmpty()) return Result.success()

        return try {
            val preferences = PreferencesManager(applicationContext)
            val executor = CommandExecutor(applicationContext)
            val volumeManager = StorageVolumeManager(applicationContext)
            val (fresh, protectedRoots) = AutomationCommon.scanAndProtect(applicationContext)

            val scopedMeta: List<FileMetadata>
            val scopedVolumes: List<StorageVolume>
            val folder = rule.folderPath
            if (folder != null) {
                scopedMeta = fresh.filter { it.absolutePath == folder || it.absolutePath.startsWith("$folder/") }
                scopedVolumes = listOf(volumeManager.asVolume(File(folder)))
            } else {
                val allVolumes = volumeManager.listVolumes()
                when (rule.storageScope) {
                    "INTERNAL" -> {
                        scopedMeta = fresh.filterNot { it.isRemovable }
                        scopedVolumes = allVolumes.filterNot { it.isRemovable }
                    }
                    "SD_CARD" -> {
                        scopedMeta = fresh.filter { it.isRemovable }
                        scopedVolumes = allVolumes.filter { it.isRemovable }
                    }
                    else -> {
                        scopedMeta = fresh
                        scopedVolumes = allVolumes
                    }
                }
            }

            val filteredMeta = if (protectedRoots.isEmpty()) scopedMeta
            else scopedMeta.filterNot { ProtectionRules.isProtected(it.absolutePath, protectedRoots) }

            val results = mutableListOf<ExecutionResult>()
            for (command in commands) {
                results += executor.execute(command, filteredMeta, scopedVolumes, protectedRoots)
            }

            if (preferences.automationNotifications.first()) {
                NotificationHelper.showSummary(
                    applicationContext,
                    "\"${rule.name}\" finished",
                    AutomationCommon.notificationSummaryText(results)
                )
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_RULE_ID = "rule_id"
    }
}
