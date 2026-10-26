package org.manuscriptwatch.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object AppNotifications {
    private const val CHANNEL = "manuscript_changes"
    fun notifyChange(context: Context, change: StatusChange) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL, "Editorial status changes", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Changes detected in manuscript status" })
        val intent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val message = "${change.before} → ${change.after}"
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("${change.manuscript.journal}: status changed")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${change.manuscript.title}\n$message"))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        manager.notify(change.manuscript.id.hashCode(), notification)
    }
}
