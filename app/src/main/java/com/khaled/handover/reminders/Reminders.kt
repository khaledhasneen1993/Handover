package com.khaled.handover.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import com.khaled.handover.MainActivity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.khaled.handover.HandoverApp
import com.khaled.handover.data.Progress
import java.util.concurrent.TimeUnit

/** WorkManager's inexact, reboot-aware one-shot scheduling; no exact-alarm permission. */
class ReturnReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("inspection") ?: return Result.failure()
        val inspection = (applicationContext as HandoverApp).repository.dao.getInspection(id) ?: return Result.success()
        if (inspection.status == Progress.ARCHIVED || inspection.status == Progress.RETURN_DONE || inspection.dueAt == null) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("handover-reminders", "Handover reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val open = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_INSPECTION_ID, id)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pending = PendingIntent.getActivity(applicationContext, id.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(id.hashCode(), NotificationCompat.Builder(applicationContext, "handover-reminders")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Upcoming handover")
            .setContentText(inspection.title + " · Review your documentation before the due date")
            .setContentIntent(pending)
            .setAutoCancel(true).build())
        return Result.success()
    }
}
object ReminderScheduler {
    fun schedule(context: Context, id: String, dueAt: Long, leadMinutes: Int) {
        require(leadMinutes in 0..10080)
        val delay = (dueAt - leadMinutes * 60000L - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ReturnReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("inspection" to id))
            .addTag("handover-reminder-$id")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("handover-reminder-$id", ExistingWorkPolicy.REPLACE, request)
    }
    fun cancel(context: Context, id: String) { WorkManager.getInstance(context).cancelUniqueWork("handover-reminder-$id") }
}
