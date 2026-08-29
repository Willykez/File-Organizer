package com.willykez.files.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.willykez.files.R

private const val CHANNEL_ID = "automation_summary"
private const val NOTIFICATION_ID = 4201

/**
 * Wraps notification posting so an automated run's outcome is visible without opening the app —
 * previously the app declared POST_NOTIFICATIONS in the manifest but never actually used it, so
 * "Daily Auto-Organize" and "Nightly Cleanup" ran completely silently even when they found and
 * moved/deleted files.
 */
object NotificationHelper {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID, "Automation summaries", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Summary after a scheduled organize/cleanup run finishes"
        }
        manager.createNotificationChannel(channel)
    }

    /** Safe to call even without notification permission granted — just silently no-ops. */
    fun showSummary(context: Context, title: String, text: String) {
        runCatching {
            ensureChannel(context)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
