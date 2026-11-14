package com.flashloop.app.scheduling

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.flashloop.app.MainActivity
import com.flashloop.app.R
import com.flashloop.app.data.Flashcard

object Notifier {
    const val CHANNEL_ID = "flashcard_reviews"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, "Flashcard reviews", NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Pops up when a flashcard is due for review" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun show(context: Context, card: Flashcard, snoozeMinutes: Int) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_REVIEW, true)
        }
        val openPi = PendingIntent.getActivity(
            context, 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snooze = Intent(context, AlarmReceiver::class.java)
            .setAction(Scheduler.ACTION_SNOOZE)
            .putExtra(Scheduler.EXTRA_MINUTES, snoozeMinutes)
        val snoozePi = PendingIntent.getBroadcast(
            context, 2, snooze, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Flashcard time")
            .setContentText(card.question)
            .setStyle(NotificationCompat.BigTextStyle().bigText(card.question))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .addAction(0, "Snooze $snoozeMinutes min", snoozePi)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
