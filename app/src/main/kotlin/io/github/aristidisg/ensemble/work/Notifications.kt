package io.github.aristidisg.ensemble.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.aristidisg.ensemble.MainActivity
import io.github.aristidisg.ensemble.R

object Notifications {
    const val CHANNEL_DAILY = "daily"
    const val CHANNEL_CHECKIN = "checkin"
    const val ID_MORNING = 1001
    const val ID_CHECKIN = 1002

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_DAILY, context.getString(R.string.notif_channel_daily), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_CHECKIN, context.getString(R.string.notif_channel_checkin), NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openApp(context: Context, target: String, arg: String? = null, request: Int = 0): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN, target)
            arg?.let { putExtra(MainActivity.EXTRA_ARG, it) }
        }
        return PendingIntent.getActivity(context, request, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun morning(context: Context, title: String, body: String, wearPayload: String?) {
        if (!canPost(context)) return
        val b = NotificationCompat.Builder(context, CHANNEL_DAILY)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp(context, MainActivity.OPEN_TODAY))
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.notif_see_more), openApp(context, MainActivity.OPEN_TODAY, request = 1))
        if (wearPayload != null) {
            val wear = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_WEAR
                putExtra(NotificationActionReceiver.EXTRA_PAYLOAD, wearPayload)
            }
            b.addAction(0, context.getString(R.string.notif_wear_it), PendingIntent.getBroadcast(context, 2, wear, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        NotificationManagerCompat.from(context).notify(ID_MORNING, b.build())
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun checkIn(context: Context, logId: String) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_CHECKIN)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.checkin_title))
            .setContentText(context.getString(R.string.notif_checkin_body))
            .setContentIntent(openApp(context, MainActivity.OPEN_CHECKIN, logId, request = 3))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_CHECKIN, n)
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)
}
