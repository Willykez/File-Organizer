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
import com.willykez.files.domain.CommandExecutor
import com.willykez.files.domain.ProtectionRules
import com.willykez.files.domain.StorageScanner
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Runs the Automation-category commands in the background on a daily cadence.
 *
 * The original app listed "Daily Auto-Organize" and "Nightly Cleanup" as selectable commands but
 * the executor treated both as `Not implemented` (a stub SKIPPED entry) — there was no scheduling
 * mechanism at all. This worker + [AutomationScheduler] is the real implementation.
 */
class AutomationWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val command = inputData.getString(KEY_COMMAND)?.let {
            runCatching { CommandType.valueOf(it) }.getOrNull()
        } ?: return Result.failure()

        return try {
            val scanner = StorageScanner(applicationContext)
            val metadataManager = MetadataManager()
            val executor = CommandExecutor(applicationContext)
            val preferences = PreferencesManager(applicationContext)

            // Re-scan so automation always acts on a fresh index rather than a stale one.
            val skipHidden = preferences.skipHiddenFolders.first()
            val fresh = scanner.scanAll(skipHidden = skipHidden)
            metadataManager.saveMetadata(fresh)

            // Automated runs respect protected folders exactly like manual ones — a scheduled
            // job is the last place a source-code or firmware folder should get shredded, since
            // there's no one watching to catch it happening.
            val autoRoots = if (preferences.autoProtectEnabled.first()) ProtectionRules.detectProtectedRoots(fresh) else emptySet()
            val userRoots = preferences.protectedFolders.first()
            val protectedRoots = autoRoots + userRoots
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
                notifySummary(command, results)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun notifySummary(command: CommandType, results: List<ExecutionResult>) {
        val succeeded = results.sumOf { it.succeeded }
        val failed = results.sumOf { it.failed }
        val title = when (command) {
            CommandType.AUTO_ORGANIZE_DAILY -> "Daily Auto-Organize finished"
            CommandType.NIGHTLY_CLEANUP -> "Nightly Cleanup finished"
            else -> "Automation finished"
        }
        val text = if (succeeded == 0 && failed == 0) "Nothing to do — everything was already tidy."
        else buildString {
            append("$succeeded item(s) handled")
            if (failed > 0) append(", $failed failed")
            append(".")
        }
        NotificationHelper.showSummary(applicationContext, title, text)
    }

    companion object {
        const val KEY_COMMAND = "command"
    }
}

object AutomationScheduler {

    private fun workName(command: CommandType) = "automation_${command.name}"

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
}
