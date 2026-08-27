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
import com.willykez.files.data.model.CommandType
import com.willykez.files.domain.CommandExecutor
import com.willykez.files.domain.StorageScanner
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

            // Re-scan so automation always acts on a fresh index rather than a stale one.
            val fresh = scanner.scanAll()
            metadataManager.saveMetadata(fresh)

            when (command) {
                CommandType.AUTO_ORGANIZE_DAILY -> {
                    executor.execute(CommandType.MOVE_DOWNLOADS, fresh)
                    executor.execute(CommandType.MOVE_SCREENSHOTS, fresh)
                }
                CommandType.NIGHTLY_CLEANUP -> {
                    executor.execute(CommandType.DELETE_TEMP_FILES, fresh)
                    executor.execute(CommandType.DELETE_EMPTY_FOLDERS, fresh)
                }
                else -> return Result.failure()
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
