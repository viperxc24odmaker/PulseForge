package com.makeforge.pulseforge

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** On Wear OS, a notification you post on the phone automatically bridges to the
 *  paired watch. So this same code = "notifications on your watch". */
object Notifications {
    private const val CHANNEL_ID = "pulseforge_alerts"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "PulseForge Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Fitness alerts that also mirror to your watch" }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun fireTest(context: Context, title: String, body: String) {
        ensureChannel(context)
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pulse)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

        if (allowed) NotificationManagerCompat.from(context).notify(1001, notif)
    }
}
