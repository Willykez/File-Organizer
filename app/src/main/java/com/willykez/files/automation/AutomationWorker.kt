package com.willykez.files.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import com.willykez.files.data.MetadataManager
import com.willykez.files.data.PreferencesManager
import com.willykez.files.data.model.CommandType
import com.willykez.files.data.model.ExecutionResult
import com.willykez.files.data.model.FileMetadata
import com.willykez.files.domain.CommandExecutor
import com.willykez.files.domain.ProtectionRules
import com.willykez.files.domain.StorageScanner
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Shared by both automation workers: a fresh scan plus the full (never-overridden) protected-root
 *  set, since neither runs with anyone present to grant an explicit override. */
internal object AutomationCommon {
    suspend fun scanAndProtect(context: Context): Pair<List<FileMetadata>, Set<String>> {
        val scanner = StorageScanner(context)
        val metadataManager = MetadataManager()
        val preferences = PreferencesManager(context)

        val skipHidden = preferences.skipHiddenFolders.first()
        val fresh = scanner.scanAll(skipHidden = skipHidden)
        metadataManager.saveMetadata(fresh)

        val autoRoots = if (preferences.autoProtectEnabled.first()) ProtectionRules.detectProtectedRoots(fresh) else emptySet()
        val userRoots = preferences.protectedFolders.first()
        return fresh to (autoRoots + userRoots)
    }

    fun notificationSummaryText(results: List<ExecutionResult>): String {
        val succeeded = results.sumOf { it.succeeded }
        val failed = results.sumOf { it.failed }
        return if (succeeded == 0 && failed == 0) "Nothing to do — everything was already tidy."
        else buildString {
            append("$succeeded item(s) handled")
            if (failed > 0) append(", $failed failed")
            append(".")
        }
    }
}

/**
 * Runs the two built-in Automation-category presets ("Daily Auto-Organize" and "Nightly Cleanup")
 * on a daily cadence.
 *
 * The original app listed these as selectable commands but the executor treated both as
 * `Not implemented` (a stub SKIPPED entry) — there was no scheduling mechanism at all. This
 * worker + [AutomationScheduler] is the real implementation. For user-defined, folder-scoped
 * automations, see [AutomationRuleWorker].
 */
class AutomationWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val command = inputData.getString(KEY_COMMAND)?.let {
            runCatching { CommandType.valueOf(it) }.getOrNull()
        } ?: return Result.failure()

        return try {
            val preferences = PreferencesManager(applicationContext)
            val executor = CommandExecutor(applicationContext)
            val (fresh, protectedRoots) = AutomationCommon.scanAndProtect(applicationContext)
            val scoped = if (protectedRoots.isEmpty()) fresh
            else fresh.filterNot { ProtectionRules.isProtected(it.absolutePath, protectedRoots) }

            val results = mutableListOf<ExecutionResult>()
            when (command) {
                CommandType.AUTO_ORGANIZE_DAILY -> {
                    results += executor.execute(CommandType.MOVE_DOWNLOADS, scoped)
                    results += executor.execute(CommandType.MOVE_SCREENSHOTS, scoped)
                }
                CommandType.NIGHTLY_CLEANUP -> {
                    results += executor.execute(CommandType.DELETE_TEMP_FILES, scoped)
                    results += executor.execute(CommandType.DELETE_EMPTY_FOLDERS, fresh, protectedRoots = protectedRoots)
                }
                else -> return Result.failure()
            }

            if (preferences.automationNotifications.first()) {
                val title = when (command) {
                    CommandType.AUTO_ORGANIZE_DAILY -> "Daily Auto-Organize finished"
                    CommandType.NIGHTLY_CLEANUP -> "Nightly Cleanup finished"
                    else -> "Automation finished"
                }
                NotificationHelper.showSummary(applicationContext, title, AutomationCommon.notificationSummaryText(results))
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_COMMAND = "command"
    }
}

object AutomationScheduler {

    private fun workName(command: CommandType) = "automation_${command.name}"
    private fun ruleWorkName(ruleId: String) = "automation_rule_$ruleId"

    fun schedule(context: Context, command: CommandType) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val request = PeriodicWorkRequestBuilder<AutomationWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInputData(androidx.work.Data.Builder().putString(AutomationWorker.KEY_COMMAND, command.name).build())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName(command), ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    fun cancel(context: Context, command: CommandType) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(command))
    }

    /** Schedules (or reschedules, if already running) a user-defined [com.willykez.files.data.model.AutomationRule]. */
    fun scheduleRule(context: Context, ruleId: String, intervalHours: Int) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val safeInterval = intervalHours.coerceIn(1, 24 * 7)
        val request = PeriodicWorkRequestBuilder<AutomationRuleWorker>(safeInterval.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInputData(androidx.work.Data.Builder().putString(AutomationRuleWorker.KEY_RULE_ID, ruleId).build())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ruleWorkName(ruleId), ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    fun cancelRule(context: Context, ruleId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(ruleWorkName(ruleId))
    }
}
